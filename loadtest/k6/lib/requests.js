import http from 'k6/http';
import { check } from 'k6';
import { BASE_URLS, pick } from './config.js';
import { authHeaders } from './auth.js';

// 낙관적 잠금 충돌(409)은 정상 응답이라 실패로 세지 않는다(docs/requirements.md 4.3).
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409));

const base = () => pick(BASE_URLS);
const internal = (storeId) => `${base()}/api/v1/internal/stores/${storeId}/products`;

function ok(res, label) {
  return check(res, { [`${label} 200`]: (r) => r.status === 200 });
}

export function listStoreProducts(storeId) {
  ok(http.get(internal(storeId), { headers: authHeaders('store'), tags: { name: 'store_list' } }), 'store_list');
}

export function effectiveOptions(storeId, productId) {
  ok(http.get(`${internal(storeId)}/${productId}/effective-options`, { headers: authHeaders('store'), tags: { name: 'store_options' } }), 'store_options');
}

export function setVisibility(storeId, productId, visibility) {
  ok(http.put(`${internal(storeId)}/${productId}/visibility`, JSON.stringify({ visibility }), { headers: authHeaders('store'), tags: { name: 'store_setting' } }), 'store_setting');
}

export function setStockStatus(storeId, productId, stockStatus) {
  ok(http.put(`${internal(storeId)}/${productId}/stock-status`, JSON.stringify({ stockStatus }), { headers: authHeaders('store'), tags: { name: 'store_setting' } }), 'store_setting');
}

export function setDisplayOrder(storeId, productIds) {
  ok(http.put(`${internal(storeId)}/display-order`, JSON.stringify({ productIds }), { headers: authHeaders('store'), tags: { name: 'store_display_order' } }), 'store_display_order');
}

export function adminSearch(keyword, status) {
  const query = status ? `status=${status}&page=0&size=20` : `keyword=${encodeURIComponent(keyword)}&page=0&size=20`;
  ok(http.get(`${base()}/api/v1/admin/products?${query}`, { headers: authHeaders('admin'), tags: { name: 'admin_search' } }), 'admin_search');
}
