import http from 'k6/http';
import { check } from 'k6';
import { BASE_URLS, INFO_BATCH, ACTIVE_IDS, pick, sample } from './config.js';
import { authHeaders } from './auth.js';

// 낙관적 잠금 충돌(409)은 정상 응답이라 실패로 세지 않는다(docs/requirements.md 4.3).
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409));

const base = () => pick(BASE_URLS);
const internal = () => `${base()}/api/v1/internal`;
const admin = () => `${base()}/api/v1/admin`;

function ok(res, label) {
  return check(res, { [`${label} 200`]: (r) => r.status === 200 });
}

// Store 서비스가 점주 화면에서 부르는 기준 정보 조회: 한 매장의 상품 ID를 100개씩 나눠 조회한다.
export function productInfo(batch = INFO_BATCH, name = 'catalog_info') {
  const ids = sample(ACTIVE_IDS, batch).join(',');
  ok(http.get(`${internal()}/products?ids=${ids}`, { headers: authHeaders('store'), tags: { name } }), name);
}

export function effectiveOptions(productId, name = 'catalog_options') {
  ok(http.get(`${internal()}/products/${productId}/effective-options`, { headers: authHeaders('store'), tags: { name } }), name);
}

export function categories() {
  ok(http.get(`${internal()}/categories`, { headers: authHeaders('store'), tags: { name: 'catalog_categories' } }), 'catalog_categories');
}

// 복제본을 처음 채우거나 다시 맞출 때: 커서를 따라 끝까지 한 페이지씩 받는다. 요청은 페이지마다 state_list로 센다.
export function walkProductStates(size = 200) {
  let cursor = null;
  for (let page = 0; page < 1000; page++) {
    const query = cursor === null ? `size=${size}` : `size=${size}&cursor=${cursor}`;
    const res = http.get(`${internal()}/product-states?${query}`, { headers: authHeaders('store'), tags: { name: 'state_list' } });
    if (!ok(res, 'state_list')) return;
    cursor = res.json('nextCursor');
    if (cursor === null) return;
  }
}

export function adminSearch(keyword, status) {
  const query = status ? `status=${status}&page=0&size=20` : `keyword=${encodeURIComponent(keyword)}&page=0&size=20`;
  ok(http.get(`${admin()}/products?${query}`, { headers: authHeaders('admin'), tags: { name: 'admin_search' } }), 'admin_search');
}

// 본사의 상품 변경: 단종했다가 다시 활성화한다. 요청마다 상품 이벤트가 하나씩 아웃박스에 쌓인다(Kafka로 나간다).
// 같은 상품을 두 VU가 동시에 바꾸지 않도록 VU마다 다른 상품을 쓴다(VU 번호는 동시에 도는 VU끼리 겹치지 않는다).
export function toggleStatus(productId) {
  const url = `${admin()}/products/${productId}`;
  const params = (name) => ({ headers: authHeaders('admin'), tags: { name } });
  const down = http.post(`${url}/discontinue`, null, params('admin_toggle'));
  if (ok(down, 'admin_toggle')) ok(http.post(`${url}/activate`, null, params('admin_toggle')), 'admin_toggle');
}
