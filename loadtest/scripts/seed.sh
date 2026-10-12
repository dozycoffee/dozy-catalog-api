#!/usr/bin/env bash
# 시드를 다시 만든다. 앱이 한 번이라도 떠서 Flyway가 스키마를 만든 뒤에 실행한다.
#   scripts/seed.sh        설계 기준(매장 3,000, 상품 200)
#   scripts/seed.sh x2     여유 규모(매장 6,000, 상품 400)
# 측정 중 변경 요청(진열·숨김·품절)이 데이터를 바꾸므로 측정 실행 전에 매번 다시 만든다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

if [ "$(psql_exec -tAc "SELECT to_regclass('public.products') IS NOT NULL" | tr -d '[:space:]')" != "t" ]; then
  echo "스키마가 없습니다. 먼저 scripts/app.sh start 1 로 앱을 한 번 띄워 Flyway를 실행하세요." >&2; exit 1
fi

args=()
case "${1:-base}" in
  base) ;;
  x2) args=(-v stores=6000 -v drinks=200 -v foods=70 -v mds=130 -v old=600 -v mdlinks=30 -v sched_name=200) ;;
  *) echo "사용법: $0 [base|x2]" >&2; exit 1 ;;
esac

echo "시드 생성 중(${1:-base})..."
time psql_exec ${args[@]+"${args[@]}"} -f - < "$LOADTEST_DIR/seed/seed.sql"
