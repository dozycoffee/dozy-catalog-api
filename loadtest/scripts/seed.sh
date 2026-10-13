#!/usr/bin/env bash
# 시드를 다시 만든다. 앱이 한 번이라도 떠서 Flyway가 스키마를 만든 뒤에 실행한다.
#   scripts/seed.sh        설계 기준(매장 3,000, 상품 200)
#   scripts/seed.sh x2     여유 규모(매장 6,000, 상품 400)
#   scripts/seed.sh nolimited  설계 기준과 같되 한정 판매 상품을 모두 전체 판매(ALL)로 바꾼다(대조 실험용).
#                          대상 매장 행(product_target_stores)이 없어서, 그 행을 읽는 비용이 요청 처리에 얼마나 드는지 볼 수 있다.
#                          매장당 취급 상품은 180개에서 200개로 늘어나 비교에 불리한 쪽으로 치우친다.
# 측정 중 변경 요청(진열·숨김·품절)이 데이터를 바꾸므로 측정 실행 전에 매번 다시 만든다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

if [ "$(psql_exec -tAc "SELECT to_regclass('public.products') IS NOT NULL" | tr -d '[:space:]')" != "t" ]; then
  echo "스키마가 없습니다. 먼저 scripts/app.sh start 1 로 앱을 한 번 띄워 Flyway를 실행하세요." >&2; exit 1
fi

args=()
case "${1:-base}" in
  base) ;;
  nolimited) ;;
  x2) args=(-v stores=6000 -v drinks=200 -v foods=70 -v mds=130 -v old=600 -v mdlinks=30 -v sched_name=200) ;;
  *) echo "사용법: $0 [base|x2|nolimited]" >&2; exit 1 ;;
esac

echo "시드 생성 중(${1:-base})..."
time psql_exec ${args[@]+"${args[@]}"} -f - < "$LOADTEST_DIR/seed/seed.sql"

if [ "${1:-base}" = nolimited ]; then
  psql_exec -c "UPDATE products SET store_scope = 'ALL' WHERE store_scope = 'LIMITED'" \
            -c "DELETE FROM product_target_stores" \
            -c "ANALYZE products" -c "ANALYZE product_target_stores"
  echo "한정 판매를 모두 전체 판매로 바꿨습니다(nolimited)."
fi
