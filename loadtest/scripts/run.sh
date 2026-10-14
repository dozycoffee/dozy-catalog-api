#!/usr/bin/env bash
# k6 시나리오를 실행하고 요약을 loadtest/.run/results/ 에 저장한다.
#   scripts/run.sh <smoke|baseline|target|ramp|spike|soak|contention> [k6 옵션...]
# 환경 변수: BASE_URLS(쉼표 구분, 기본 8081,8082), LOADTEST_AUTH_URL, PRODUCT_SCALE(x2 시드면 2)
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

profile="${1:?사용법: $0 <smoke|baseline|target|ramp|spike|soak|contention>}"
shift

command -v k6 > /dev/null || { echo "k6가 필요합니다 (brew install k6)" >&2; exit 1; }

# 측정 전 장비 상태. 다른 프로세스가 CPU를 쓰고 있으면 같은 부하에서도 결과가 크게 흔들린다(로컬 측정에서 270ms~4초까지 관찰).
if cores="$(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null)"; then
  load1="$(uptime | sed -E 's/.*load averages?: *([0-9.]+).*/\1/')"
  echo "장비: ${cores}코어, 로드 평균(1분) ${load1}"
  if awk "BEGIN { exit !($load1 > $cores * 0.4) }"; then
    echo "경고: 로드 평균이 코어의 40%를 넘습니다. 브라우저·IDE 등 CPU를 쓰는 프로세스를 닫고(그라파나 탭 포함) 잠시 쉰 뒤 실행하면 결과가 덜 흔들립니다." >&2
    # head가 파이프를 먼저 닫아도(SIGPIPE) 스크립트가 멈추지 않게 한다
    { ps -Ao pcpu,comm -r | head -4 | awk '{printf "  %s%%  %s\n", $1, $2}' >&2; } || true
  fi
fi

first_url="$(echo "${BASE_URLS:-$DEFAULT_BASE_URLS}" | cut -d, -f1)"
revision="$(curl -s "$first_url/actuator/prometheus" | grep '^dozy_catalog_build_info' | sed -E 's/.*revision="([^"]*)".*/\1/' || true)"
echo "측정 대상 revision: ${revision:-알 수 없음}"

# 쿼리 통계가 이번 실행만 반영하도록 비운다(결과는 scripts/top-queries.sh로 본다)
"$LOADTEST_DIR/scripts/top-queries.sh" --reset > /dev/null 2>&1 || true

results="$RUN_DIR/results"
mkdir -p "$results"
stamp="$(date +%Y%m%d-%H%M%S)"
summary="$results/$stamp-$profile-${revision:0:7}.json"

k6 run \
  -e PROFILE="$profile" \
  -e BASE_URLS="${BASE_URLS:-$DEFAULT_BASE_URLS}" \
  -e AUTH_URL="$AUTH_URL" \
  -e PRODUCT_SCALE="${PRODUCT_SCALE:-1}" \
  --summary-export "$summary" \
  "$@" "$LOADTEST_DIR/k6/main.js"

echo "요약: $summary"
