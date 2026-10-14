// 실행 프로필(__ENV.PROFILE). 처리량과 지연 목표는 docs/requirements.md 4.3 표와 같은 값이다. 거기가 바뀌면 여기도 바꾼다.
// 측정 대상은 Catalog가 받는 요청(Store 서비스용 조회, 본사 API)이다. 점주·손님의 매장 요청은 Store의 몫이라 없다(ADR-0020).

// 요청별 목표 처리량(초당). 정상 피크의 2배다.
export const TARGET_RPS = {
  catalog_info: 30, // 상품 기준 정보 조회(ID 100개)
  catalog_options: 4, // 유효 옵션 조회
  catalog_categories: 0.2, // 카테고리 목록 (처리량 목표는 없고 지연만 본다. 요청이 끊기지 않을 정도로만 보낸다)
  state_list: 1 / 60, // 복제본 채우기: 상태 목록을 끝까지 훑는 횟수(초당). 한 번에 약 3페이지. 처리량 목표는 없고 지연만 본다
  admin_search: 2, // 본사 상품 검색·목록
  admin_toggle: 0.5, // 본사 상품 변경(단종 + 활성화). 상품 이벤트를 만든다. 요구사항의 "본사 API 2 rps"에 속하지만 따로 센다
};

// 요청별 지연 목표 [p95, p99] (ms)
export const LATENCY_MS = {
  catalog_info: [300, 800],
  catalog_options: [100, 300],
  catalog_categories: [100, 300],
  state_list: [1000, 2000], // 페이지 하나(200건)
  admin_search: [500, 1000],
  admin_toggle: [500, 1000],
};

// 단계: [지속 시간, 목표 처리량 대비 배율]. 앞의 ramp 단계는 천천히 올리고, 뒤의 hold 단계는 유지한다.
const WARMUP = { startTime: '0s', stages: [['2m', 1], ['1m', 1]] }; // 워밍업(JIT, 풀, 캐시)은 측정 구간에서 뺀다
const MEASURE_START = '3m';
// 정상 피크 = 목표의 0.5배(목표는 정상 피크의 2배). 아래 시나리오 프로필은 정상 피크에서 본다.
const NORMAL = 0.5;
const NORMAL_WARMUP = { startTime: '0s', stages: [['2m', NORMAL], ['1m', NORMAL]] };

// 캐시 콜드 스타트: Store 두 대의 유효 옵션 캐시가 비어 상품 200개를 인스턴스마다 한 번씩(400건) 약 10초에 다시 조회한다(약 40 rps).
const coldBurst = (startTime) => ({
  executor: 'constant-arrival-rate',
  exec: 'cold_options',
  rate: 40,
  timeUnit: '1s',
  duration: '10s',
  startTime,
  preAllocatedVUs: 30,
  maxVUs: 100,
});

// 복제본 채우기: 분당 2번(두 인스턴스가 동시에 채우는 정도) 상태 목록을 끝까지 훑는다.
const replicaWalk = (startTime, duration) => ({
  executor: 'constant-arrival-rate',
  exec: 'state_list',
  rate: 2,
  timeUnit: '1m',
  duration,
  startTime,
  preAllocatedVUs: 4,
  maxVUs: 10,
});

export const PROFILES = {
  // 모든 요청이 200인지만 본다. 처리량이 낮은 요청도 빠지지 않게 도착률이 아니라 횟수로 실행한다
  smoke: { iterations: 10, thresholds: 'checks' },
  // 낮은 부하(목표의 10%)의 기준선
  baseline: { warmup: { startTime: '0s', stages: [['1m', 0.1]] }, measure: { startTime: '1m', stages: [['2m', 0.1]] }, thresholds: 'all' },
  // 목표 처리량을 10분 유지하고 지연·오류율 목표로 판정한다
  target: { warmup: WARMUP, measure: { startTime: MEASURE_START, stages: [['10m', 1]] }, thresholds: 'all' },
  // 한계 찾기: 배율을 올려 가며 어디서 꺾이는지 본다(판정 아님)
  ramp: {
    measure: {
      startTime: '0s',
      stages: [['1m', 0.5], ['2m', 0.5], ['1m', 1], ['2m', 1], ['1m', 1.5], ['2m', 1.5], ['1m', 2], ['2m', 2], ['1m', 3], ['2m', 3]],
    },
    thresholds: 'none',
  },
  // 스파이크: 정상 피크의 3배 = 목표의 1.5배를 5분. 오류율 기준만 적용한다
  spike: { warmup: WARMUP, measure: { startTime: MEASURE_START, stages: [['30s', 1.5], ['5m', 1.5]] }, thresholds: 'errors' },
  // 한 대 장애(N-1): 한 대를 내린 상태에서 정상 피크(= 목표의 0.5배)를 처리하는지. BASE_URLS를 남은 한 대로 준다
  n1: { warmup: NORMAL_WARMUP, measure: { startTime: MEASURE_START, stages: [['10m', NORMAL]] }, thresholds: 'all' },
  // 내구: 목표 처리량을 30분 유지
  soak: { warmup: WARMUP, measure: { startTime: MEASURE_START, stages: [['30m', 1]] }, thresholds: 'all' },
  // 캐시 콜드 스타트: 정상 피크(기준 정보 조회, 본사 검색)가 계속되는 동안 유효 옵션 조회가 약 40 rps로 10초씩 세 번 몰린다.
  // 기준 정보 조회와 본사 API의 지연이 목표를 넘지 않아야 하고, 몰린 요청도 실패하지 않아야 한다.
  cold_start: {
    only: ['catalog_info', 'admin_search'],
    warmup: NORMAL_WARMUP,
    measure: { startTime: MEASURE_START, stages: [['4m', NORMAL]] },
    thresholds: 'all',
    extras: { cold_burst_1: coldBurst('3m30s'), cold_burst_2: coldBurst('4m30s'), cold_burst_3: coldBurst('5m30s') },
    extraThresholds: { 'http_req_failed{name:catalog_options_cold}': ['rate<0.001'], 'http_req_duration{name:catalog_options_cold}': ['p(95)<1e9', 'p(99)<1e9'] },
  },
  // 복제본 채우기: 정상 피크가 계속되는 동안 상태 목록을 분당 2번 끝까지 훑는다(지연은 페이지 하나 기준).
  replica_fill: {
    only: ['catalog_info', 'admin_search'],
    warmup: NORMAL_WARMUP,
    measure: { startTime: MEASURE_START, stages: [['5m', NORMAL]] },
    thresholds: 'all',
    extras: { measure_walk: replicaWalk(MEASURE_START, '5m') },
    extraThresholds: {
      'http_req_duration{scenario:measure_walk,name:state_list}': [`p(95)<${LATENCY_MS.state_list[0]}`, `p(99)<${LATENCY_MS.state_list[1]}`],
      'http_req_failed{scenario:measure_walk,name:state_list}': ['rate<0.001'],
    },
  },
  // 예약 적용·Kafka 장애 시나리오의 배경 부하: 정상 피크에서 상품 변경(이벤트 발행)을 계속한다. scripts/events.sh가 이 부하를 걸고
  // 대기 예약의 적용 시각을 당기거나 Kafka를 내렸다 올린다. 변경 API의 지연·오류율이 목표 안인지 본다.
  events: {
    only: ['catalog_info', 'admin_search', 'admin_toggle'],
    warmup: NORMAL_WARMUP,
    measure: { startTime: MEASURE_START, stages: [['8m', NORMAL]] },
    thresholds: 'all',
  },
};

const perMinute = (rps, scale) => Math.max(1, Math.round(rps * scale * 60));

const UNIT_SECONDS = { s: 1, m: 60, h: 3600 };
const seconds = (duration) => parseInt(duration) * UNIT_SECONDS[duration.slice(-1)];

// 측정 구간에서 도착해야 하는 요청 수(단계마다 배율이 일정하다고 본 근사)
const expectedIterations = (rps, phase) =>
  phase.stages.reduce((sum, [duration, scale]) => sum + seconds(duration) * (perMinute(rps, scale) / 60), 0);

// 시작하지 못한 요청이 의도한 요청의 0.1%를 넘으면 실패다(요구사항 4.3 오류율 기준과 같은 값).
const DROPPED_TOLERANCE = 0.001;

function arrivalScenario(name, rps, phase) {
  const peakScale = Math.max(...phase.stages.map(([, scale]) => scale));
  const peakRps = rps * peakScale;
  return {
    executor: 'ramping-arrival-rate',
    exec: name,
    timeUnit: '1m',
    startRate: perMinute(rps, phase.stages[0][1]),
    startTime: phase.startTime,
    // 응답이 느려져도 도착률을 지키도록 VU를 넉넉히 준다
    // 응답이 0.6초까지 느려져도 새 VU를 만들지 않고 받도록 미리 할당한다(VU 지연 생성이 만드는 누락을 줄이려는 값)
    preAllocatedVUs: Math.max(2, Math.ceil(peakRps * 0.6) + 2),
    maxVUs: Math.ceil(peakRps * 2.5) + 10,
    stages: phase.stages.map(([duration, scale]) => ({ target: perMinute(rps, scale), duration })),
  };
}

export function buildOptions(profileName) {
  const profile = PROFILES[profileName];
  if (!profile) throw new Error(`알 수 없는 PROFILE: ${profileName} (${Object.keys(PROFILES).join(', ')}, probe)`);

  const scenarios = {};
  const thresholds = {};
  for (const [name, rps] of Object.entries(TARGET_RPS)) {
    if (profile.only && !profile.only.includes(name)) continue;
    if (profile.iterations) {
      scenarios[`measure_${name}`] = { executor: 'shared-iterations', exec: name, vus: 2, iterations: profile.iterations };
      continue;
    }
    if (profile.warmup) scenarios[`warm_${name}`] = arrivalScenario(name, rps, profile.warmup);
    scenarios[`measure_${name}`] = arrivalScenario(name, rps, profile.measure);

    // 같은 VU가 부르는 토큰 발급(name:auth_token)이 섞이지 않게 요청 이름까지 고른다
    const selector = `{scenario:measure_${name},name:${name}}`;
    if (profile.thresholds === 'all') {
      const [p95, p99] = LATENCY_MS[name];
      thresholds[`http_req_duration${selector}`] = [`p(95)<${p95}`, `p(99)<${p99}`];
    }
    if (profile.thresholds === 'all' || profile.thresholds === 'errors') {
      thresholds[`http_req_failed${selector}`] = ['rate<0.001'];
      // 서버가 못 따라가 k6가 시작하지 못한 요청도 실패로 본다. 오류율은 실제로 보낸 요청만 세므로
      // 이게 없으면 부하의 대부분을 못 보내고도 오류율이 낮게 나온다(ramp에서 의도한 요청의 88%가 시작되지 못했다).
      // dropped_iterations에는 name 태그가 없어서 시나리오만 고른다.
      thresholds[`dropped_iterations{scenario:measure_${name}}`] = [`count<=${Math.floor(expectedIterations(rps, profile.measure) * DROPPED_TOLERANCE)}`];
    }
    if (profile.thresholds === 'none') {
      // 임계값이 없으면 k6가 서브 메트릭을 만들지 않아 결과 요약에 안 나오므로, 항상 통과하는 값을 건다
      thresholds[`http_req_duration${selector}`] = ['p(95)<1e9', 'p(99)<1e9'];
      thresholds[`http_req_failed${selector}`] = ['rate<1.01'];
    }
  }
  if (profile.thresholds === 'checks') thresholds.checks = ['rate==1'];
  Object.assign(scenarios, profile.extras || {});
  Object.assign(thresholds, profile.extraThresholds || {});

  return { scenarios, thresholds, discardResponseBodies: false };
}

// probe: 요청 하나를 낮은 고정 도착률로 짧게 재서 요청별 비용(지연, 요청당 CPU)을 본다. 포화시키지 않으므로 줄(큐)이 생기지 않고,
// 요청당 CPU처럼 부하 수준과 거의 무관한 값을 2분 안에 얻는다. 수정 전후 비교에 쓴다. 실행은 scripts/probe.sh.
//   환경 변수: PROBE(요청 이름), PROBE_RATE(초당, 기본은 아래), PROBE_WARMUP(기본 30s), PROBE_DURATION(기본 60s)
export const PROBE_DEFAULT_RPS = {
  catalog_info: 10,
  catalog_options: 20,
  catalog_categories: 5,
  state_list: 0.5, // 초당 0.5번 훑는다(한 번에 약 3페이지)
  admin_search: 5,
  admin_toggle: 2,
};

export function probeOptions() {
  const name = __ENV.PROBE;
  if (!TARGET_RPS[name]) throw new Error(`PROBE는 ${Object.keys(TARGET_RPS).join(', ')} 중 하나여야 한다 (받은 값: ${name})`);
  const rps = parseFloat(__ENV.PROBE_RATE || PROBE_DEFAULT_RPS[name]);
  const warmup = __ENV.PROBE_WARMUP || '30s';
  const duration = __ENV.PROBE_DURATION || '60s';

  const scenario = (startTime, scenarioDuration) => ({
    executor: 'constant-arrival-rate',
    exec: name,
    timeUnit: '1m',
    rate: perMinute(rps, 1),
    duration: scenarioDuration,
    startTime,
    preAllocatedVUs: Math.max(2, Math.ceil(rps * 0.6) + 2),
    maxVUs: Math.ceil(rps * 5) + 10,
  });

  const selector = `{scenario:measure_${name},name:${name}}`;
  return {
    scenarios: { [`warm_${name}`]: scenario('0s', warmup), [`measure_${name}`]: scenario(warmup, duration) },
    // 판정이 아니라 측정이다. 임계값이 없으면 k6가 서브 메트릭을 만들지 않아 요약에 안 나오므로, 항상 통과하는 값을 건다
    thresholds: {
      [`http_req_duration${selector}`]: ['p(95)<1e9', 'p(99)<1e9'],
      [`http_req_failed${selector}`]: ['rate<1.01'],
      [`dropped_iterations{scenario:measure_${name}}`]: ['count<1e9'],
    },
  };
}
