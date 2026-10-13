#!/usr/bin/env bash
# 요청 하나를 낮은 고정 부하로 짧게 재서 서버 지연과 요청당 CPU를 출력한다(약 2분). 수정 전후 비교와 요청별 비용 비교에 쓴다.
#   scripts/probe.sh <store_list|store_options|store_setting|store_display_order|admin_search> [초당 요청 수] [측정 초=60]
# 앱과 DB, dozy-auth가 떠 있고 시드가 있어야 한다(README). 포화시키지 않는 낮은 부하라 줄이 생기지 않는다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

name="${1:?사용법: $0 <store_list|store_options|store_setting|store_display_order|admin_search> [rps] [측정 초]}"
rate="${2:-}"
measure="${3:-60}"

echo "유휴 CPU 측정(약 15초)..."
sleep 15
idle="$(python3 "$LOADTEST_DIR/scripts/probe-report.py" idle)"

extra=(-e PROBE="$name" -e PROBE_DURATION="${measure}s")
[ -n "$rate" ] && extra+=(-e PROBE_RATE="$rate")

"$LOADTEST_DIR/scripts/run.sh" probe "${extra[@]}" > "$RUN_DIR/probe-last.out" 2>&1 || {
  tail -20 "$RUN_DIR/probe-last.out" >&2; echo "k6 실행이 실패했습니다. 전체 출력: $RUN_DIR/probe-last.out" >&2; exit 1
}
end="$(date +%s)"
sed -n '/^장비/,/^측정 대상/p' "$RUN_DIR/probe-last.out"

sleep 8  # 마지막 수집(5초 간격)이 반영될 때까지
python3 "$LOADTEST_DIR/scripts/probe-report.py" report "$name" "$end" $((measure - 6)) "$idle"
echo "  상위 쿼리(이번 실행):"
"$LOADTEST_DIR/scripts/top-queries.sh" 3 2>/dev/null | sed 's/^/    /'
