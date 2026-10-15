// 부하테스트 진입점. 실행은 scripts/run.sh <프로필>.
//   프로필: smoke, baseline, target, ramp, spike, n1, soak, cold_start, replica_fill, events, probe(scripts/probe.sh)
import { buildOptions, probeOptions } from './lib/profiles.js';
import { adminSearch, categories, effectiveOptions, productInfo, toggleStatus, walkProductStates } from './lib/requests.js';
import { ACTIVE, OPTION_IDS, pick } from './lib/config.js';

const profile = __ENV.PROFILE || 'smoke';
export const options = profile === 'probe' ? probeOptions() : buildOptions(profile);

// 아래 export 이름은 lib/profiles.js의 TARGET_RPS 키와 같아야 한다(시나리오의 exec).

// Store 점주 화면: 한 매장의 상품 ID 100개를 조회한다
export function catalog_info() {
  productInfo();
}

// 상품 하나의 유효 옵션 구성
export function catalog_options() {
  effectiveOptions(pick(OPTION_IDS));
}

export function catalog_categories() {
  categories();
}

// 복제본 채우기: 상태 목록을 끝까지 한 번 훑는다(한 번에 약 3페이지)
export function state_list() {
  walkProductStates();
}

export function admin_search() {
  if (Math.random() < 0.7) {
    adminSearch(`Item ${1 + Math.floor(Math.random() * ACTIVE)}`);
  } else {
    adminSearch(null, 'ACTIVE');
  }
}

// 본사의 상품 변경 + 이벤트 발행. VU마다 다른 상품을 쓴다
export function admin_toggle() {
  toggleStatus(1 + (__VU % ACTIVE));
}

// 캐시 콜드 스타트: Store 두 대의 유효 옵션 캐시가 한꺼번에 비어 상품 전체를 한 번씩 다시 조회한다(profiles.js cold_start)
let coldIndex = 0;
export function cold_options() {
  effectiveOptions(OPTION_IDS[(coldIndex++ + __VU) % OPTION_IDS.length], 'catalog_options_cold');
}
