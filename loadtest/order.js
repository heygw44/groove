// 주문 생성 부하 테스트 (#193, W9) — 다중 상품 주문(POST /api/v1/orders)을 ramping-vus 로 측정한다.
//
// 시드 사용자 풀(loadtest001..080@groove.test / Test1234!)의 Bearer 토큰으로 매 iteration 마다 서로 다른
// 앨범 3종을 주문한다. 재고 차감은 락 없이 단순 구현된 W6 경로(W10 개선 시작점)이며, 본 부하는 그
// 처리량/지연 Before 베이스라인을 박제하는 것이 목적이다 — 동시성 정합성은 W9-3에서 별도 측정한다.
//
// 시드 앨범 일부(SOLD_OUT/HIDDEN/stock=1, 약 8%)는 409 재고부족·422 구매불가로 거절되는데, 이는 정상
// 도메인 응답이므로 expectedStatuses 로 http_req_failed 에서 제외하고 order_rejected 로 별도 집계한다.
//
// 선행: 앱 기동 + scripts/seed.sh 로 메인 시드 적용(앨범 50,000건 + loadtest001..080@groove.test / Test1234!).
//       setup 의 로그인 버스트가 throttle 되지 않도록 AUTH_RATE_LIMIT_LOGIN_CAPACITY 를 크게 주입한다.
// 실행: k6 run loadtest/order.js

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import { buildTokenPool, seedEmail } from './lib/auth.js';
import { buildOrderBody } from './lib/orders.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MEMBER_COUNT = Number(__ENV.MEMBER_COUNT || 80);
const ALBUM_COUNT = Number(__ENV.ALBUM_COUNT || 50000);
const PASSWORD = __ENV.PASSWORD || 'Test1234!';
const EMAIL_PREFIX = __ENV.EMAIL_PREFIX || 'loadtest';
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || '@groove.test';
const ITEMS_PER_ORDER = 3;

const orderCreated = new Counter('order_created'); // 201 주문 생성 성공
const orderRejected = new Counter('order_rejected'); // 409 재고부족 / 422 구매불가(정상 도메인 거절)
const orderLatency = new Trend('order_latency', true); // 주문 요청 지연(setup 로그인 제외)

export const options = {
  scenarios: {
    place_order: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 50 }, // 워밍
        { duration: '40s', target: 50 }, // 지속 주문 부하
        { duration: '10s', target: 0 }, // 감쇠
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // setup 로그인 버스트(phase:setup-login)가 섞이지 않도록 주문 요청(phase:order)만 게이트한다.
    'http_req_failed{phase:order}': ['rate<0.01'],
    // order_latency 는 생성 성공(201)한 주문만 집계(setup·거절 제외). SLO 초깃값.
    order_latency: ['p(95)<800', 'p(99)<1500'],
    // 주문을 최소 1건은 실제로 생성해야 한다 — 전량 거절(409/422)·404 로 0건 생성되는 진공 통과 방지.
    order_created: ['count>0'],
    checks: ['rate>0.99'],
  },
  // k6 기본 요약은 p(99) 를 출력하지 않으므로 명시 — README 의 p95·p99 가 산출물에 드러나도록.
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const email = seedEmail(EMAIL_PREFIX, 3, EMAIL_DOMAIN); // loadtest001@groove.test ...

// 시드된 회원 N명을 로그인시켜 토큰 풀을 만든다 (1회).
export function setup() {
  const tokens = buildTokenPool({ baseUrl: BASE_URL, count: MEMBER_COUNT, password: PASSWORD, email });
  return { tokens };
}

export default function (data) {
  // 시나리오 전역 반복 카운터로 토큰·앨범을 인덱싱 — 풀 크기까지 회원이 겹치지 않고 앨범도 분산된다.
  const n = exec.scenario.iterationInTest;
  const token = data.tokens[n % data.tokens.length];
  const body = buildOrderBody({ n, albumCount: ALBUM_COUNT, itemCount: ITEMS_PER_ORDER });

  const res = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify(body), {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    tags: { phase: 'order' },
    // 409 재고부족·422 구매불가는 정상 도메인 응답 → 실패 집계에서 제외(5xx/연결오류만 http_req_failed).
    // 404(앨범 없음)는 일부러 제외한다 — ALBUM_COUNT 가 실제 시드 앨범 수보다 크면 범위 밖 id 가 404 가 되고,
    // 그때 http_req_failed·checks 가 breach 되어 설정 불일치를 빨리 드러낸다(README: ALBUM_COUNT ≤ 시드 수).
    responseCallback: http.expectedStatuses(201, 409, 422),
  });

  if (res.status === 201) {
    orderCreated.add(1);
    orderLatency.add(res.timings.duration); // 생성 성공 요청만 SLO 집계(빠른 거절 혼입 방지)
  } else if (res.status === 409 || res.status === 422) {
    orderRejected.add(1);
  }

  check(res, {
    '201 주문 생성 (또는 409/422 도메인 거절)': (r) => r.status === 201 || r.status === 409 || r.status === 422,
    'orderNumber 존재(201 시)': (r) => r.status !== 201 || !!r.json('orderNumber'),
  });
}

export function handleSummary(data) {
  return {
    'loadtest/order-summary.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
