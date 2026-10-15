// 환경 변수로 바꾸는 값과, seed/seed.sql의 데이터 규칙에 기댄 상품 id 규칙.
export const BASE_URLS = (__ENV.BASE_URLS || 'http://localhost:8081,http://localhost:8082').split(',');
export const AUTH_URL = __ENV.AUTH_URL || 'http://localhost:8080';

// seed.sh의 규모와 맞춘다: base = 1(상품 200), x2 = 2(상품 400)
export const PRODUCT_SCALE = parseInt(__ENV.PRODUCT_SCALE || '1');

// 상품 id는 [음료][푸드][MD][단종 누적] 순서로 1부터 연속이다. ACTIVE는 앞의 ACTIVE개다.
const DRINKS = 100 * PRODUCT_SCALE;
const FOODS = 35 * PRODUCT_SCALE;
const MD_WITH_OPTIONS = 15 * PRODUCT_SCALE;
export const ACTIVE = 200 * PRODUCT_SCALE;

// Store가 한 번에 조회하는 상품 수의 상한(docs/api/store-service.md). 매장 상품 약 180개를 100개씩 나눠 부른다.
export const INFO_BATCH = 100;

function range(from, to) {
  const ids = [];
  for (let id = from; id <= to; id++) ids.push(id);
  return ids;
}

// ACTIVE 상품 전체(판매 범위가 한정인 것도 섞여 있다). 기준 정보 조회와 상태 변경 대상
export const ACTIVE_IDS = range(1, ACTIVE);
// 옵션 그룹이 연결된 상품. 유효 옵션 조회 대상
export const OPTION_IDS = range(1, DRINKS + FOODS + MD_WITH_OPTIONS);

export const pick = (array) => array[Math.floor(Math.random() * array.length)];

// 서로 다른 n개를 무작위로 고른다(Store가 한 매장의 상품 ID를 모아 보내는 것을 흉내 낸다).
export function sample(array, n) {
  const copy = array.slice();
  const count = Math.min(n, copy.length);
  for (let i = 0; i < count; i++) {
    const j = i + Math.floor(Math.random() * (copy.length - i));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy.slice(0, count);
}
