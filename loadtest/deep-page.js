// 커서(keyset) vs offset 깊은 페이지 응답시간 비교 (#235, M16) — 같은 "깊이"의 페이지를 offset
// (LIMIT … OFFSET N) 과 keyset(cursor) 두 경로로 요청해 지연을 맞대어 본다. offset 은 깊이에 비례해
// 건너뛸 행을 스캔하므로 깊은 페이지일수록 느려지지만, keyset 은 정렬 인덱스(V21)를 타고 위치를 바로
// 짚어 깊이와 무관하게 상수에 가깝다 — 그 격차를 박제하는 것이 목적이다(이슈 AC: deep-offset 대비 개선 측정).
//
// 선행: 앱 기동 + db/seed 적재(ALBUM_COUNT=50000 기본). 토큰 풀은 search.js 와 동일한 시드 회원
//       (loadtest001..080@groove.test / Test1234!)을 쓴다. setup 로그인 버스트가 throttle 되지 않도록
//       AUTH_RATE_LIMIT_LOGIN_CAPACITY 를 크게 주입한다.
// 실행: k6 run -e DEEP_ROW=10000 loadtest/deep-page.js
//       → loadtest/deep-page-summary.json + stdout 에 offset/keyset p95 비교 출력
//       정렬키별(#244): -e SORT=price,desc / -e SORT=releaseYear,desc 로 V25 커버링 인덱스 경로도 측정

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import { buildTokenPool, seedEmail } from './lib/auth.js';
import { redactSetupTokens } from './lib/summary.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MEMBER_COUNT = Number(__ENV.MEMBER_COUNT || 80);
const PASSWORD = __ENV.PASSWORD || 'Test1234!';
const EMAIL_PREFIX = __ENV.EMAIL_PREFIX || 'loadtest';
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || '@groove.test';

const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 20);
const DEEP_ROW = Number(__ENV.DEEP_ROW || 10000); // 비교 대상 깊이(대략 N번째 행)
const SORT = __ENV.SORT || 'createdAt,desc'; // 정렬 화이트리스트 내 — id tiebreaker 는 서버가 보강
const WALK_SIZE = 100; // 커서 워밍업 walk 스텝 크기(= max-page-size, setup 요청 수 절감용)

const DEEP_PAGE = Math.floor(DEEP_ROW / PAGE_SIZE); // offset 페이지 번호

const offsetDeepLatency = new Trend('offset_deep_latency', true); // offset 깊은 페이지 지연
const keysetDeepLatency = new Trend('keyset_deep_latency', true); // keyset 동일 깊이 지연

export const options = {
  scenarios: {
    deep_page: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 30 }, // 워밍
        { duration: '40s', target: 30 }, // 지속 부하
        { duration: '10s', target: 0 }, // 감쇠
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // 측정 요청(phase:measure)만 게이트 — setup 워밍업/로그인 지연이 글로벌 지표를 오염시키지 않도록.
    'http_req_failed{phase:measure}': ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  // 깊은 페이지 비교는 꼬리 지연이 핵심이라 p95/p99 를 요약에 명시.
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const email = seedEmail(EMAIL_PREFIX, 3, EMAIL_DOMAIN);

// 토큰 풀 + DEEP_ROW 근처의 keyset 커서 1개를 setup 에서 1회 확보한다.
// 커서는 WALK_SIZE(=100) 로 걸어 요청 수를 줄인다(예: DEEP_ROW=10000 → 약 100회). 시드가 DEEP_ROW
// 보다 작으면 마지막 페이지 직전 커서를 그대로 쓴다.
export function setup() {
  const tokens = buildTokenPool({ baseUrl: BASE_URL, count: MEMBER_COUNT, password: PASSWORD, email });
  const auth = { headers: { Authorization: `Bearer ${tokens[0]}` }, tags: { phase: 'warmup' } };

  let cursor = null;
  let walked = 0;
  while (walked < DEEP_ROW) {
    const q = `size=${WALK_SIZE}&sort=${SORT}` + (cursor ? `&cursor=${encodeURIComponent(cursor)}` : '');
    const res = http.get(`${BASE_URL}/api/v1/albums/scroll?${q}`, auth);
    if (res.status !== 200) throw new Error(`warmup scroll 실패: status=${res.status}`);
    const body = res.json();
    walked += body.content ? body.content.length : 0;
    if (!body.hasNext) break; // 시드가 DEEP_ROW 보다 작음 — 마지막 커서로 측정
    cursor = body.nextCursor;
  }
  if (!cursor) {
    throw new Error('깊은 커서를 만들지 못했다 — 시드 규모/DEEP_ROW 를 확인하라(ALBUM_COUNT 기본 50000)');
  }
  console.log(`깊은 커서 확보: 약 ${walked}행 지점 (비교 offset page≈${DEEP_PAGE}, size=${PAGE_SIZE})`);
  return { tokens, deepCursor: cursor };
}

// 반복 인덱스 패리티로 offset/keyset 을 번갈아 때려 같은 부하 분포에서 두 경로를 비교한다.
export default function (data) {
  const n = exec.scenario.iterationInTest;
  const headers = { Authorization: `Bearer ${data.tokens[n % data.tokens.length]}` };

  if (n % 2 === 0) {
    const res = http.get(
      `${BASE_URL}/api/v1/albums?page=${DEEP_PAGE}&size=${PAGE_SIZE}&sort=${SORT}`,
      { headers, tags: { phase: 'measure', mode: 'offset' } },
    );
    offsetDeepLatency.add(res.timings.duration);
    check(res, { 'offset 200': (r) => r.status === 200 });
  } else {
    const q = `cursor=${encodeURIComponent(data.deepCursor)}&size=${PAGE_SIZE}&sort=${SORT}`;
    const res = http.get(`${BASE_URL}/api/v1/albums/scroll?${q}`, {
      headers,
      tags: { phase: 'measure', mode: 'keyset' },
    });
    keysetDeepLatency.add(res.timings.duration);
    check(res, { 'keyset 200': (r) => r.status === 200 });
  }
}

export function handleSummary(data) {
  // offset vs keyset p95 격차를 산출해 로그로 박제(k6 threshold 는 두 메트릭 비교가 불가하므로 여기서 판정).
  const offsetP95 = data.metrics.offset_deep_latency?.values?.['p(95)'] || 0;
  const keysetP95 = data.metrics.keyset_deep_latency?.values?.['p(95)'] || 0;
  const improvement = offsetP95 > 0 ? (1 - keysetP95 / offsetP95) * 100 : 0;
  console.log(
    `[#235 deep-page] offset p95=${offsetP95.toFixed(1)}ms vs keyset p95=${keysetP95.toFixed(1)}ms ` +
      `→ keyset 가 ${improvement.toFixed(1)}% 빠름 (DEEP_ROW=${DEEP_ROW}, offset page=${DEEP_PAGE}, size=${PAGE_SIZE})`,
  );

  redactSetupTokens(data); // setup 토큰 풀(실제 JWT) 비식별화(#219). deepCursor 는 공개 정렬 튜플이라 무해.
  return {
    'loadtest/deep-page-summary.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
