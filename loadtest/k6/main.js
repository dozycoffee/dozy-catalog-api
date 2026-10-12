// 부하테스트 진입점. 실행은 scripts/run.sh <프로필>.
//   프로필: smoke, baseline, target, ramp, spike, n1, soak, contention
import { buildOptions, contentionOptions } from './lib/profiles.js';
import { adminSearch, effectiveOptions, listStoreProducts, setDisplayOrder, setStockStatus, setVisibility } from './lib/requests.js';
import { ACTIVE, ALL_SCOPE_IDS, OPTION_IDS, UNTRACKED_IDS, pick, randomStore } from './lib/config.js';

const profile = __ENV.PROFILE || 'smoke';
export const options = profile === 'contention' ? contentionOptions() : buildOptions(profile);

function shuffled(ids) {
  const copy = ids.slice();
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

// 아래 export 이름은 lib/profiles.js의 TARGET_RPS 키와 같아야 한다(시나리오의 exec).
export function store_list() {
  listStoreProducts(randomStore());
}

export function store_options() {
  effectiveOptions(randomStore(), pick(OPTION_IDS));
}

// 숨김·노출 전환 2/3, 수동 품절 1/3
export function store_setting() {
  const storeId = randomStore();
  if (Math.random() < 2 / 3) {
    setVisibility(storeId, pick(ALL_SCOPE_IDS), Math.random() < 0.5 ? 'HIDDEN' : 'VISIBLE');
  } else {
    setStockStatus(storeId, pick(UNTRACKED_IDS), Math.random() < 0.5 ? 'SOLD_OUT' : 'ON_SALE');
  }
}

export function store_display_order() {
  setDisplayOrder(randomStore(), shuffled(ALL_SCOPE_IDS));
}

export function admin_search() {
  if (Math.random() < 0.7) {
    adminSearch(`Item ${1 + Math.floor(Math.random() * ACTIVE)}`);
  } else {
    adminSearch(null, 'ACTIVE');
  }
}

// 매장 1, 2에만 진열 순서 변경을 계속 보낸다
export function contention() {
  setDisplayOrder(1 + (__VU % 2), shuffled(ALL_SCOPE_IDS));
}
