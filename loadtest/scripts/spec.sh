#!/usr/bin/env bash
# 인스턴스 사양(CPU, 메모리, 풀 크기) 하나로 프로필을 돌리고 JVM·컨테이너 지표를 요약한다(요구사항 4.4 "사양 조정").
#   scripts/spec.sh <CPU수> <메모리 예: 1536m> [프로필...]     기본 프로필: target spike
# 환경 변수: LOADTEST_POOL_SIZE(풀 크기, 기본 10)는 그대로 쓴다. 사양을 바꾸려면 앱을 다시 띄우므로 이 스크립트가 start까지 한다.
# 결과 요약은 .run/spec-<cpu>-<메모리>-풀<n>.txt에도 쌓는다. 한 번에 하나만 바꿔 가며 부른다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

cpus="${1:?사용법: $0 <CPU수> <메모리> [프로필...]}"
memory="${2:?사용법: $0 <CPU수> <메모리> [프로필...]}"
shift 2
profiles=("${@:-target spike}")
if [ "$#" -eq 0 ]; then profiles=(target spike); fi

pool="${LOADTEST_POOL_SIZE:-10}"
out="$RUN_DIR/spec-${cpus}cpu-${memory}-pool${pool}.txt"
stats="$RUN_DIR/spec-stats.log"

export LOADTEST_APP_CPUS="$cpus" LOADTEST_APP_MEMORY="$memory" LOADTEST_POOL_SIZE="$pool"
{
  echo "== 사양: Catalog ×2, ${cpus} vCPU, ${memory}, 풀 ${pool} ($(date '+%Y-%m-%d %H:%M')) =="
} | tee -a "$out"

"$LOADTEST_DIR/scripts/app.sh" start 2 > /dev/null
"$LOADTEST_DIR/scripts/seed.sh" > /dev/null

for profile in "${profiles[@]}"; do
  "$LOADTEST_DIR/scripts/seed.sh" > /dev/null
  : > "$stats"
  (
    while true; do
      now="$(date +%s)"
      docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' loadtest-catalog-1-1 loadtest-catalog-2-1 2>/dev/null \
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
    # 시작·종료 직후의 워밍업을 포함한 전체 구간이다
    python3 "$LOADTEST_DIR/scripts/spec-report.py" "$profile" "$start" "$end" "$stats"
    n="$(grep -n '█ THRESHOLDS' "$RUN_DIR/spec-$profile.out" | tail -1 | cut -d: -f1)"
    [ -n "$n" ] && sed -n "${n},/█ TOTAL RESULTS/p" "$RUN_DIR/spec-$profile.out" | grep -E "✗" | sed 's/^/  위반: /' || true
  } | tee -a "$out"
done

for c in loadtest-catalog-1-1 loadtest-catalog-2-1; do
  echo "$c: OOMKilled=$(docker inspect -f '{{.State.OOMKilled}}' "$c") 재시작=$(docker inspect -f '{{.RestartCount}}' "$c")" | tee -a "$out"
done
