// 결제·멱등성 부하 테스트 (#193, W9) — 결제 요청(POST /api/v1/payments)을 ramping-vus 로 측정하고,
// 동일 Idempotency-Key 재요청이 중복 결제를 만들지 않음을 부하 상황에서 check 로 검증한다.
//
// 한 iteration = (1) 결제 대상 PENDING 주문 1건 생성(phase:order-setup, 측정 제외)
//              → (2) 멱등키 1개로 결제 2회(phase:payment) → 두 응답의 paymentId 동일성 검증.
// 결제 API 는 @Idempotent 라 Idempotency-Key 헤더가 필수(없으면 400)다. 정상 흐름은 첫 요청·재요청 모두
// 202 Accepted 이고, 재요청은 캐시된 첫 응답을 그대로 replay 하므로 paymentId 가 같아야 한다(중복 결제 0).
//
// 멱등 fingerprint = orderNumber + "|" + method 이므로 동일 키 + 동일 body 는 replay, 다른 body 면 409.
// 본 시나리오는 항상 동일 body 라 항상 replay 된다.
//
// 선행: 앱 기동 + scripts/seed.sh 로 메인 시드 적용(앨범 50,000건 + loadtest001..080@groove.test / Test1234!).
//       setup 의 로그인 버스트가 throttle 되지 않도록 AUTH_RATE_LIMIT_LOGIN_CAPACITY 를 크게 주입한다.
// 실행: k6 run loadtest/payment.js

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { buildTokenPool, seedEmail } from './lib/auth.js';
import { buildOrderBody } from './lib/orders.js';
import { redactedSummary } from './lib/summary.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MEMBER_COUNT = Number(__ENV.MEMBER_COUNT || 80);
const ALBUM_COUNT = Number(__ENV.ALBUM_COUNT || 50000);
const PASSWORD = __ENV.PASSWORD || 'Test1234!';
const EMAIL_PREFIX = __ENV.EMAIL_PREFIX || 'loadtest';
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || '@groove.test';

const paymentRequested = new Counter('payment_requested'); // 첫 결제 202 접수
const paymentDuplicated = new Counter('payment_duplicated'); // 멱등 위반(재요청이 다른 paymentId) — 0 이어야 함
const orderSetupFailed = new Counter('order_setup_failed'); // 결제 전 주문 생성 실패(측정 제외, 해당 iteration 결제 스킵)
const paymentLatency = new Trend('payment_latency', true); // 첫 결제 요청 지연(주문 생성·재요청 제외)

export const options = {
  scenarios: {
    pay_order: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        // iteration 당 요청 3회(주문 1 + 결제 2)라 검색/주문보다 VU 목표를 낮춘다.
        { duration: '10s', target: 30 }, // 워밍
        { duration: '40s', target: 30 }, // 지속 결제 부하
        { duration: '10s', target: 0 }, // 감쇠
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // setup 로그인·주문 생성(phase:order-setup)이 섞이지 않도록 결제 요청(phase:payment)만 게이트한다.
    'http_req_failed{phase:payment}': ['rate<0.01'],
    // payment_latency 는 접수 성공(202)한 첫 결제만 집계한다. 이 SLO 는 앱을 PAYMENT_MOCK_DELAY_MIN/MAX=0
    // 으로 띄운 전제 — 기본 Mock PG 지연(100~500ms)을 끄지 않으면 가짜 sleep 때문에 breach 된다(README 참조).
    payment_latency: ['p(95)<800', 'p(99)<1500'],
    // 멱등성 하드 게이트 — 부하 중에도 동일 키 재요청은 중복 결제를 단 1건도 만들면 안 된다.
    payment_duplicated: ['count==0'],
    // 결제를 최소 1건은 실제로 접수해야 한다 — 주문 생성이 전량 실패해 결제 0건으로 끝나는 진공 통과 방지.
    payment_requested: ['count>0'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const email = seedEmail(EMAIL_PREFIX, 3, EMAIL_DOMAIN); // loadtest001@groove.test ...

// 시드된 회원 N명을 로그인시켜 토큰 풀을 만든다 (1회).
export function setup() {
  const tokens = buildTokenPool({ baseUrl: BASE_URL, count: MEMBER_COUNT, password: PASSWORD, email });
  return { tokens };
}

export default function (data) {
  const n = exec.scenario.iterationInTest;
  const token = data.tokens[n % data.tokens.length];
  const authHeaders = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

  // 1) 결제할 PENDING 주문을 만든다(측정 제외 phase). 실패하면 이 iteration 은 결제를 스킵한다.
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify(buildOrderBody({ n, albumCount: ALBUM_COUNT, itemCount: 1 })),
    {
      headers: authHeaders,
      tags: { phase: 'order-setup' },
      responseCallback: http.expectedStatuses(201, 409, 422),
    },
  );
  if (orderRes.status !== 201) {
    orderSetupFailed.add(1);
    return;
  }
  const orderNumber = orderRes.json('orderNumber');

  // 2) 멱등키 1개로 결제 2회 — 동일 키 재요청이 중복 결제를 만들지 않아야 한다.
  const idemKey = uuidv4();
  const payBody = JSON.stringify({ orderNumber, method: 'MOCK' });
  const payHeaders = { ...authHeaders, 'Idempotency-Key': idemKey };

  const first = http.post(`${BASE_URL}/api/v1/payments`, payBody, {
    headers: payHeaders,
    tags: { phase: 'payment' },
  });
  if (first.status === 202) {
    paymentRequested.add(1);
    paymentLatency.add(first.timings.duration); // 접수 성공한 첫 결제만 SLO 집계(실패·재요청 혼입 방지)
  }

  const replay = http.post(`${BASE_URL}/api/v1/payments`, payBody, {
    headers: payHeaders,
    tags: { phase: 'payment' },
  });

  // paymentId 는 202 일 때만 파싱한다 — 비202(또는 비-JSON 에러 바디)에서 json() 을 호출하면 undefined 로
  // 거짓 통과하거나 파싱 예외로 iteration 이 죽는다.
  const id1 = first.status === 202 ? first.json('paymentId') : null;
  const id2 = replay.status === 202 ? replay.json('paymentId') : null;
  // 둘 다 접수됐는데 paymentId 가 다르면 멱등성 위반(중복 결제). 0 이어야 한다.
  if (first.status === 202 && replay.status === 202 && id1 !== id2) {
    paymentDuplicated.add(1);
  }

  check(first, {
    '첫 결제 202 접수': (r) => r.status === 202,
  });
  check(replay, {
    '재요청도 202': (r) => r.status === 202,
    // 두 결제가 모두 접수(202)되고 같은 paymentId 일 때만 통과 — 실패 결제에서의 거짓 통과 방지.
    '멱등 replay: 동일 paymentId (중복 결제 없음)': () => id1 != null && id1 === id2,
  });
}

export function handleSummary(data) {
  // setup() 토큰 풀(실제 JWT)을 비식별화한 뒤 요약을 기록한다 — 평문 토큰 유출 방지(#219).
  return redactedSummary(data, 'loadtest/payment-summary.json');
}
