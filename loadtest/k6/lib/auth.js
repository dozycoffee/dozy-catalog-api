import http from 'k6/http';
import { check } from 'k6';
import { AUTH_URL } from './config.js';

// dozy-auth의 개발용 토큰 API(POST /dev/tokens, local·dev 프로필 전용)로 토큰을 만든다.
// 모듈 변수는 VU마다 따로라서 VU가 자기 토큰을 갖고, 만료 1분 전에 다시 받는다.
const KINDS = {
  store: { principalType: 'system', principalId: '0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f', roles: ['catalog:store_agent'] },
  admin: { principalType: 'employee', principalId: '0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d60', roles: ['catalog:admin'] },
};
const cache = {};

function issue(kind) {
  const res = http.post(
    `${AUTH_URL}/dev/tokens`,
    JSON.stringify({ realm: 'internal', ...KINDS[kind] }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth_token' } },
  );
  if (!check(res, { '토큰 발급 200': (r) => r.status === 200 })) {
    throw new Error(`토큰을 받지 못했습니다: ${res.status} ${res.body}`);
  }
  const body = res.json();
  return { token: body.accessToken, expiresAt: Date.now() + body.expiresIn * 1000 };
}

export function token(kind) {
  const cached = cache[kind];
  if (cached && cached.expiresAt - Date.now() > 60000) return cached.token;
  cache[kind] = issue(kind);
  return cache[kind].token;
}

export function authHeaders(kind) {
  return { Authorization: `Bearer ${token(kind)}`, 'Content-Type': 'application/json' };
}
