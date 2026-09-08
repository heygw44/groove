// #316 핵심 증명 — 사전 집계 배치(POST /api/v1/admin/stats/aggregations)가 도는 동안 일반 API
// (GET /api/v1/products) 응답 시간이 평상시와 같은지를 재는 시나리오. 지연을 배치로 "옮긴" 게 아니라
// "없앴다"는 걸 보이는 유일한 방법이라 이 스크립트가 #316 의 핵심이다.
//
// ## 20260909 1차 측정의 함정과 재설계
// 1차 버전은 트리거를 per-vu-iterations 1(1회성)로만 쏘고 0~2분/2~6분을 pretest/posttest 로 나눠
// 구간 p95 를 비교했다. 그런데 실제 배치 호출은 901ms 만에 끝났다 — 4분짜리 posttest 구간 중 배치가
// 실제로 도는 시간은 0.9초뿐이었다. posttest p95 는 대부분 "배치가 돌지 않는 동안"의 응답을 잰 값이라
// pretest 대비 +1.84% 라는 결론이 배치 간섭이 없다는 근거가 되지 못했다(희석).
//
// 고친 방법: trigger 시나리오를 1회성이 아니라 posttest 구간 내내 반복한다. 한 VU 가 순차 루프를 돌며
// 재집계를 계속 다시 트리거하고(멱등이라 안전), 매 호출의 시작/종료 epoch(ms)를 콘솔에 로그로 남긴다.
// 단일 VU 가 순차로만 호출하므로 named lock(AggregationLock) 경합에 의한 409 자체가 구조적으로 생기지
// 않는다 — 동시에 여러 트리거를 던지는 대신 "겹치지 않게 쉬지 않고 반복"하는 쪽을 택했다.
//
// baseline 요청은 이제 시점(phase) 태그만 붙이고, "배치가 실제로 도는 동안"인지는 실행이 끝난 뒤
// analyze-batch-interference.mjs 가 k6 raw JSON 출력(--out json)의 요청 타임스탬프와 콘솔 로그에 남은
// 배치 시작/종료 구간을 직접 대조해 판정한다. 시간 구간을 미리 나누는 방식은 배치 길이가 구간보다
// 짧아지면 다시 같은 함정에 빠지므로, 사후 대조로 "실제로 배치가 도는 순간"만 골라낸다.
//
// 시나리오 구성:
//   baseline  : constant-arrival-rate 50rps, 6분, GET /api/v1/products (product-list.js 와 같은 쿼리 변형)
//   dashboard : constant-arrival-rate 0.5rps, 6분, 관리자 통계 4종 동시 호출
//   trigger   : per-vu-iterations 1(단일 VU), startTime 2분 ~ 종료 직전까지 자체 루프로
//               POST /api/v1/admin/stats/aggregations {from: D-90, to: D-1} 를 순차 반복
//
// 실행 (6분+ 걸리므로 반드시 백그라운드로, raw JSON 출력을 남겨야 사후 분석이 가능하다):
//   k6 run --out json=scripts/k6/results/batch-interference-raw.json scripts/k6/batch-interference.js
// 환경변수: BASE_URL(기본 http://localhost:8080), ADMIN_EMAIL/ADMIN_PASSWORD
//           (기본 admin@groove.com/admin1234!), RESULT_DIR(기본 scripts/k6/results)
// 합성 데이터가 있는 스키마(backend/scripts/perf/seed-load-db.sh 로 만든 groove_load 등)에 붙은
// 백엔드가 필요하다.
//
// 판정은 scripts/k6/analyze-batch-interference.mjs 가 낸다: 배치가 실제로 도는 동안(duringBatch)의
// baseline p95 를 평시(pretest) p95 와 비교(+10% 이내), 절대값 NFR-03 의 300ms, http_req_failed 0.

import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@groove.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin1234!';
const RESULT_DIR = __ENV.RESULT_DIR || 'scripts/k6/results';

// 2분 지점부터 배치 구간(posttest) 시작. trigger 시나리오의 startTime 과 맞춘다.
// TRIGGER_START/TOTAL_DURATION 은 스모크 테스트에서만 짧게 덮어쓴다(예: TRIGGER_START=10s TOTAL_DURATION=30s).
const TRIGGER_START = __ENV.TRIGGER_START || '2m';
const TOTAL_DURATION = __ENV.TOTAL_DURATION || '6m';
const BATCH_START_MS = parseDurationMs(TRIGGER_START);
const TOTAL_DURATION_MS = parseDurationMs(TOTAL_DURATION);
// trigger 루프가 전체 테스트 종료 시각을 넘겨 걸치지 않도록 두는 여유. 이 안쪽까지만 새 배치 호출을 시작한다.
const TRIGGER_SAFETY_MARGIN_MS = 1500;

function parseDurationMs(text) {
  const match = text.match(/^(\d+(?:\.\d+)?)(ms|s|m|h)$/);
  if (!match) {
    throw new Error(`유효하지 않은 기간 표현식: ${text}`);
  }
  const value = Number(match[1]);
  const unitMs = { ms: 1, s: 1000, m: 60000, h: 3600000 }[match[2]];
  return value * unitMs;
}

const baselinePretestDuration = new Trend('baseline_pretest_duration');
const baselinePosttestDuration = new Trend('baseline_posttest_duration');

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    baseline: {
      executor: 'constant-arrival-rate',
      exec: 'baselineTraffic',
      rate: 50,
      timeUnit: '1s',
      duration: TOTAL_DURATION,
      preAllocatedVUs: 80,
      maxVUs: 150,
    },
    dashboard: {
      executor: 'constant-arrival-rate',
      exec: 'dashboardTraffic',
      // k6 의 rate 는 정수만 허용해 0.5rps 를 timeUnit 을 2s 로 늘려 표현한다(2초당 1회 = 0.5rps).
      rate: 1,
      timeUnit: '2s',
      duration: TOTAL_DURATION,
      preAllocatedVUs: 5,
      maxVUs: 10,
    },
    trigger: {
      // 단일 VU, 단일 iteration. 그 안에서 posttest 구간이 끝날 때까지 순차 루프를 돈다 — 동시에 여러
      // 개를 던지는 대신 "겹치지 않게 쉬지 않고 반복"해서 posttest 구간을 배치 실행 시간으로 채운다.
      executor: 'per-vu-iterations',
      exec: 'triggerLoop',
      vus: 1,
      iterations: 1,
      startTime: TRIGGER_START,
    },
  },
  thresholds: {
    // 판정 기준 절대값: NFR-03 의 300ms 는 baseline 전체(구간 무관)에도 항상 유지돼야 한다.
    'http_req_duration{scenario:baseline}': ['p(95)<300'],
    'http_req_failed': ['rate<0.01'],
    // 참고용 구간별(pretest/posttest 시간 절반) p95 안전망. 진짜 판정(배치가 실제로 도는 동안)은
    // analyze-batch-interference.mjs 가 raw JSON + 배치 구간 로그를 대조해서 낸다.
    'baseline_pretest_duration': ['p(95)<300'],
    'baseline_posttest_duration': ['p(95)<300'],
  },
};

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export function setup() {
  const genreRes = http.get(`${BASE_URL}/api/v1/genres`);
  const artistRes = http.get(`${BASE_URL}/api/v1/artists`);
  const productRes = http.get(`${BASE_URL}/api/v1/products`);

  if (genreRes.status !== 200 || artistRes.status !== 200 || productRes.status !== 200) {
    fail('시드 데이터가 없습니다. seed-load-db.sh 로 합성 데이터를 넣은 스키마에 백엔드를 붙이세요.');
  }
  const productBody = productRes.json();
  if (!productBody.data || productBody.data.totalElements === 0) {
    fail('시드 데이터가 없습니다. seed-load-db.sh 로 합성 데이터를 넣은 스키마에 백엔드를 붙이세요.');
  }

  const genreIds = genreRes.json('data').map((genre) => genre.id);
  const artistIds = artistRes.json('data').map((artist) => artist.id);

  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: ADMIN_EMAIL,
    password: ADMIN_PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup_admin_login' } });
  if (loginRes.status !== 200) {
    fail(`관리자 로그인에 실패했습니다. status=${loginRes.status} body=${loginRes.body}`);
  }

  const today = new Date();
  const from = isoDate(new Date(today.getTime() - 90 * 86400000));
  const to = isoDate(new Date(today.getTime() - 1 * 86400000));

  const testStartMs = Date.now();
  // analyze-batch-interference.mjs 가 raw JSON 의 절대 타임스탬프를 이 기준 시각과 대조해 phase 를 가른다.
  console.log(`TEST_START_MS=${testStartMs}`);

  return {
    genreIds,
    artistIds,
    adminToken: loginRes.json('data.accessToken'),
    testStartMs,
    aggregationRange: { from, to },
  };
}

function isoDate(date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function productListVariants(data) {
  return [
    { name: 'list-default', query: () => '' },
    { name: 'list-page', query: () => 'page=1&size=20' },
    { name: 'list-genre', query: () => `genreIds=${pick(data.genreIds)}` },
    { name: 'list-artist', query: () => `artistId=${pick(data.artistIds)}` },
    { name: 'list-price', query: () => 'minPrice=20000&maxPrice=50000' },
    { name: 'list-sort', query: () => `sort=${pick(['priceAsc', 'priceDesc', 'rating', 'popular'])}` },
  ];
}

// pretest(0~2분)/posttest(2~6분) 태그는 참고용 안전망일 뿐이다. 진짜 "배치가 실제로 도는 동안"
// 판정은 analyze-batch-interference.mjs 가 raw JSON 타임스탬프와 배치 구간 로그를 대조해서 낸다.
export function baselineTraffic(data) {
  const elapsed = Date.now() - data.testStartMs;
  const phase = elapsed < BATCH_START_MS ? 'pretest' : 'posttest';

  const variant = pick(productListVariants(data));
  const query = variant.query();
  const url = query ? `${BASE_URL}/api/v1/products?${query}` : `${BASE_URL}/api/v1/products`;

  const res = http.get(url, { tags: { name: 'baseline-products', phase } });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'success is true': (r) => r.json('success') === true,
  });

  if (phase === 'pretest') {
    baselinePretestDuration.add(res.timings.duration);
  } else {
    baselinePosttestDuration.add(res.timings.duration);
  }
}

export function dashboardTraffic(data) {
  const headers = { Authorization: `Bearer ${data.adminToken}` };

  const responses = http.batch([
    ['GET', `${BASE_URL}/api/v1/admin/stats/daily-sales`, null, { headers, tags: { name: 'daily-sales' } }],
    ['GET', `${BASE_URL}/api/v1/admin/stats/popular-products`, null,
      { headers, tags: { name: 'popular-products' } }],
    ['GET', `${BASE_URL}/api/v1/admin/stats/limited-drops`, null, { headers, tags: { name: 'limited-drops' } }],
    ['GET', `${BASE_URL}/api/v1/admin/stats/summary`, null, { headers, tags: { name: 'summary' } }],
  ]);

  responses.forEach((res) => {
    check(res, { 'status is 200': (r) => r.status === 200 });
  });
}

// 단일 VU, 단일 iteration 안에서 posttest 구간(TOTAL_DURATION 끝 - 안전 여유)이 찰 때까지 재집계를
// 순차로 반복 트리거한다. 같은 range 를 반복 호출해도 AggregationService.aggregateDate 는 날짜별
// UPSERT 라 멱등이다. 단일 VU 라 named lock(AggregationLock) 경합·409 가 구조적으로 생기지 않는다.
export function triggerLoop(data) {
  const headers = { Authorization: `Bearer ${data.adminToken}`, 'Content-Type': 'application/json' };
  const body = JSON.stringify(data.aggregationRange);
  let seq = 0;

  while (Date.now() - data.testStartMs < TOTAL_DURATION_MS - TRIGGER_SAFETY_MARGIN_MS) {
    seq += 1;
    const startMs = Date.now();
    const res = http.post(`${BASE_URL}/api/v1/admin/stats/aggregations`, body,
        { headers, tags: { name: 'trigger-aggregation' } });
    const endMs = Date.now();

    check(res, { 'status is 200 or 409': (r) => r.status === 200 || r.status === 409 });
    if (res.status !== 200 && res.status !== 409) {
      fail(`집계 트리거가 예상 밖 상태코드를 반환했다. status=${res.status} body=${res.body}`);
    }

    // analyze-batch-interference.mjs 가 이 줄을 파싱해 "배치가 실제로 도는 구간"을 복원한다.
    console.log(`AGG_BATCH_INTERVAL seq=${seq} startMs=${startMs} endMs=${endMs} `
        + `durationMs=${(endMs - startMs).toFixed(3)} status=${res.status}`);
  }

  console.log(`AGG_BATCH_LOOP_DONE totalCalls=${seq}`);
}

function pad(value) {
  return value < 10 ? `0${value}` : `${value}`;
}

function timestamp() {
  const now = new Date();
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
}

export function handleSummary(data) {
  const output = {};
  output['stdout'] = textSummary(data, { indent: ' ', enableColors: true });
  output[`${RESULT_DIR}/batch-interference-${timestamp()}.json`] = JSON.stringify(data, null, 2);
  return output;
}
