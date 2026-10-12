// 실행 프로필(__ENV.PROFILE). 처리량과 지연 목표는 docs/requirements.md 4.3 표와 같은 값이다. 거기가 바뀌면 여기도 바꾼다.

// 요청별 목표 처리량(초당). 정상 피크의 2배다.
export const TARGET_RPS = {
  store_list: 285,
  store_options: 180,
  store_setting: 6,
  store_display_order: 0.2,
  admin_search: 2,
};

// 요청별 지연 목표 [p95, p99] (ms)
export const LATENCY_MS = {
  store_list: [300, 800],
  store_options: [200, 500],
  store_setting: [200, 500],
  store_display_order: [500, 1000],
  admin_search: [500, 1000],
};

// 단계: [지속 시간, 목표 처리량 대비 배율]. 앞의 ramp 단계는 천천히 올리고, 뒤의 hold 단계는 유지한다.
const WARMUP = { startTime: '0s', stages: [['2m', 1], ['1m', 1]] }; // 워밍업(JIT, 풀, 캐시)은 측정 구간에서 뺀다
const MEASURE_START = '3m';

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
  n1: { warmup: { startTime: '0s', stages: [['2m', 0.5], ['1m', 0.5]] }, measure: { startTime: MEASURE_START, stages: [['10m', 0.5]] }, thresholds: 'all' },
  // 내구: 목표 처리량을 30분 유지
  soak: { warmup: WARMUP, measure: { startTime: MEASURE_START, stages: [['30m', 1]] }, thresholds: 'all' },
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
  if (!profile) throw new Error(`알 수 없는 PROFILE: ${profileName} (${Object.keys(PROFILES).join(', ')}, contention)`);

  const scenarios = {};
  const thresholds = {};
  for (const [name, rps] of Object.entries(TARGET_RPS)) {
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

  return { scenarios, thresholds, discardResponseBodies: false };
}

// 같은 매장에 진열 순서 변경이 동시에 몰리는 경우. 매장 단위 권고 잠금으로 직렬화되는 비용을 본다(요구사항 4.3).
export function contentionOptions() {
  return {
    scenarios: {
      contention: { executor: 'constant-vus', exec: 'contention', vus: 20, duration: '2m' },
    },
    thresholds: {
      'http_req_failed{scenario:contention,name:store_display_order}': ['rate<0.001'],
      'http_req_duration{scenario:contention,name:store_display_order}': ['p(95)<1e9', 'p(99)<1e9'],
    },
  };
}
