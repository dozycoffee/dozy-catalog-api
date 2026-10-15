#!/usr/bin/env bash
# 인스턴스 사양(CPU, 메모리, 풀 크기) 하나로 프로필을 돌리고 JVM·컨테이너 지표를 요약한다(요구사항 4.4 "사양 조정").
#   scripts/spec.sh <CPU수> <메모리 예: 1536m> [프로필...]     기본 프로필: target spike
# 환경 변수: LOADTEST_POOL_SIZE(풀 크기, 기본 10), LOADTEST_INSTANCES(앱 인스턴스 수, 기본 2. 1이면 k6가 첫 인스턴스만 부른다). 사양을 바꾸려면 앱을 다시 띄우므로 이 스크립트가 start까지 한다.
# 결과 요약은 .run/spec-<cpu>-<메모리>-풀<n>.txt에도 쌓는다. 한 번에 하나만 바꿔 가며 부른다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

cpus="${1:?사용법: $0 <CPU수> <메모리> [프로필...]}"
memory="${2:?사용법: $0 <CPU수> <메모리> [프로필...]}"
shift 2
profiles=("${@:-target spike}")
if [ "$#" -eq 0 ]; then profiles=(target spike); fi

pool="${LOADTEST_POOL_SIZE:-10}"
instances="${LOADTEST_INSTANCES:-2}"
out="$RUN_DIR/spec-${cpus}cpu-${memory}-pool${pool}-x${instances}.txt"

if [ "$instances" = 1 ]; then export BASE_URLS="http://localhost:${CATALOG_PORT_1}"; fi
export LOADTEST_APP_CPUS="$cpus" LOADTEST_APP_MEMORY="$memory" LOADTEST_POOL_SIZE="$pool"
{
  echo "== 사양: Catalog ×${instances}, ${cpus} vCPU, ${memory}, 풀 ${pool} ($(date '+%Y-%m-%d %H:%M')) =="
} | tee -a "$out"

"$LOADTEST_DIR/scripts/app.sh" start "$instances" > /dev/null
"$LOADTEST_DIR/scripts/seed.sh" > /dev/null

# 프로필마다 시작 후 이 시간(초)까지는 워밍업이라 측정 구간에서 뺀다(k6/lib/profiles.js의 MEASURE_START와 같다)
measure_offset() {
  case "$1" in target|spike|soak|n1|cold_start|replica_fill|events) echo 180 ;; baseline) echo 60 ;; *) echo 0 ;; esac
}

for profile in "${profiles[@]}"; do
  "$LOADTEST_DIR/scripts/seed.sh" > /dev/null
  stats="$RUN_DIR/spec-stats-${cpus}cpu-${memory}-pool${pool}-x${instances}-$profile.log"
  : > "$stats"
  (
    while true; do
      now="$(date +%s)"
      docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' $(for i in $(seq 1 "$instances"); do echo "loadtest-catalog-$i-1"; done) 2>/dev/null \
        | awk -v ts="$now" '{print ts, $0}' >> "$stats"
      sleep 3
    done
  ) &
  sampler=$!
  start="$(date +%s)"
  "$LOADTEST_DIR/scripts/run.sh" "$profile" > "$RUN_DIR/spec-$profile.out" 2>&1 && status=0 || status=$?
  end="$(date +%s)"
  kill "$sampler" 2>/dev/null || true
  wait "$sampler" 2>/dev/null || true
  sleep 6  # 마지막 수집(5초 간격)이 반영될 때까지
  {
    echo "[$profile] k6 종료 코드 $status (임계값 위반이면 0이 아니다)"
    # JVM·CPU·풀 지표는 워밍업을 뺀 측정 구간, 컨테이너 메모리는 워밍업을 포함한 전체 구간이다
    python3 "$LOADTEST_DIR/scripts/spec-report.py" "$profile" "$start" "$((start + $(measure_offset "$profile")))" "$end" "$stats"
    n="$(grep -n '█ THRESHOLDS' "$RUN_DIR/spec-$profile.out" | tail -1 | cut -d: -f1)"
    [ -n "$n" ] && sed -n "${n},/█ TOTAL RESULTS/p" "$RUN_DIR/spec-$profile.out" | grep -E "✗" | sed 's/^/  위반: /' || true
  } | tee -a "$out"
done

for c in $(for i in $(seq 1 "$instances"); do echo "loadtest-catalog-$i-1"; done); do
  echo "$c: OOMKilled=$(docker inspect -f '{{.State.OOMKilled}}' "$c") 재시작=$(docker inspect -f '{{.RestartCount}}' "$c")" | tee -a "$out"
done
