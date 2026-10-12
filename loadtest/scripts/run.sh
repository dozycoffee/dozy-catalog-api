#!/usr/bin/env bash
# k6 시나리오를 실행하고 요약을 loadtest/.run/results/ 에 저장한다.
#   scripts/run.sh <smoke|baseline|target|ramp|spike|soak|contention> [k6 옵션...]
# 환경 변수: BASE_URLS(쉼표 구분, 기본 8081,8082), LOADTEST_AUTH_URL, PRODUCT_SCALE(x2 시드면 2)
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

profile="${1:?사용법: $0 <smoke|baseline|target|ramp|spike|soak|contention>}"
shift

command -v k6 > /dev/null || { echo "k6가 필요합니다 (brew install k6)" >&2; exit 1; }

first_url="$(echo "${BASE_URLS:-http://localhost:8081}" | cut -d, -f1)"
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
  -e BASE_URLS="${BASE_URLS:-http://localhost:8081,http://localhost:8082}" \
  -e AUTH_URL="$AUTH_URL" \
  -e PRODUCT_SCALE="${PRODUCT_SCALE:-1}" \
  --summary-export "$summary" \
  "$@" "$LOADTEST_DIR/k6/main.js"

echo "요약: $summary"
