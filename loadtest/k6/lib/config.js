// 환경 변수로 바꾸는 값과, seed/seed.sql의 데이터 규칙에 기댄 상품 id 규칙.
export const BASE_URLS = (__ENV.BASE_URLS || 'http://localhost:8081,http://localhost:8082').split(',');
export const AUTH_URL = __ENV.AUTH_URL || 'http://localhost:8080';

// seed.sh의 규모와 맞춘다: base = 1(매장 3,000), x2 = 2(매장 6,000)
export const PRODUCT_SCALE = parseInt(__ENV.PRODUCT_SCALE || '1');
export const STORES = parseInt(__ENV.STORES || String(3000 * PRODUCT_SCALE));

// 상품 id는 [음료][푸드][MD] 순서로 연속이다. id % 20 < 3 이면 LIMITED(일부 매장에서만 판매)다.
const DRINKS = 100 * PRODUCT_SCALE;
const FOODS = 35 * PRODUCT_SCALE;
const MD_WITH_OPTIONS = 15 * PRODUCT_SCALE;
export const ACTIVE = 200 * PRODUCT_SCALE;

function range(from, to) {
  const ids = [];
  for (let id = from; id <= to; id++) ids.push(id);
  return ids;
}
const isAllScope = (id) => id % 20 >= 3;

// 모든 매장에서 판매하는(판매 범위 ALL) ACTIVE 상품. 숨김 전환과 진열 순서 변경 대상
export const ALL_SCOPE_IDS = range(1, ACTIVE).filter(isAllScope);
// 재고 미추적 상품. 수동 품절 대상
export const UNTRACKED_IDS = range(1, DRINKS + FOODS).filter(isAllScope);
// 옵션 그룹이 연결된 상품. 유효 옵션 조회 대상
export const OPTION_IDS = range(1, DRINKS + FOODS + MD_WITH_OPTIONS).filter(isAllScope);

export const pick = (array) => array[Math.floor(Math.random() * array.length)];
export const randomStore = () => 1 + Math.floor(Math.random() * STORES);
