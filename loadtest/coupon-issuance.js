// 선착순 쿠폰 발급 부하 테스트 (#93) — 프로덕션 원자적 조건부 UPDATE 경로(POST /coupons/{id}/issue) 스파이크.
//
// 측정: TPS·p95 지연·소진 시점·정확성(발급 성공 == 한정수량). 3종 전략 정확성/처리량 비교는 인프로세스
// JUnit(CouponIssuanceConcurrencyTest)이 담당하고, 본 스크립트는 HTTP 계층의 현실적 처리량을 측정한다.
//
// 선행: 앱 기동 + loadtest/seed-coupon-loadtest.sql 적용(쿠폰 1건 + 회원 600명). rate limit 은
//       COUPON_RATE_LIMIT_ISSUE_CAPACITY / AUTH_RATE_LIMIT_LOGIN_CAPACITY 를 크게 주입해 간섭을 제거한다.
// 실행: k6 run -e COUPON_ID=<id> loadtest/coupon-issuance.js

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { redactedSummary } from './lib/summary.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const COUPON_ID = __ENV.COUPON_ID || '1';
const MEMBER_COUNT = Number(__ENV.MEMBER_COUNT || 600);
const PASSWORD = __ENV.PASSWORD || 'Loadtest123!';
const EMAIL_PREFIX = __ENV.EMAIL_PREFIX || 'loadtest-';
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || '@groove.test';

const issued = new Counter('coupon_issued'); // 201 발급 성공
const rejected = new Counter('coupon_rejected'); // 409 소진/이미발급
const issueLatency = new Trend('issue_latency', true); // 발급 요청 지연(setup 로그인 제외)

export const options = {
  scenarios: {
    spike_issue: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2s', target: 50 }, // 워밍
        { duration: '5s', target: 250 }, // 스파이크 — 한정 100장에 동시 쇄도
        { duration: '3s', target: 0 }, // 감쇠
      ],
      gracefulRampDown: '2s',
    },
  },
  thresholds: {
    // 201·409 는 정상 응답이므로 expectedStatuses 로 실패 집계에서 제외 — http_req_failed 는 5xx/연결오류만.
    http_req_failed: ['rate<0.01'],
    issue_latency: ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
};

const email = (i) => `${EMAIL_PREFIX}${String(i).padStart(5, '0')}${EMAIL_DOMAIN}`;

// 시드된 회원 N명을 로그인시켜 토큰 풀을 만든다 (1회). VU 별로 서로 다른 회원 토큰을 쓰기 위함.
export function setup() {
  const tokens = [];
  for (let i = 1; i <= MEMBER_COUNT; i++) {
    const res = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({ email: email(i), password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' }, tags: { phase: 'setup-login' } },
    );
    if (res.status === 201 || res.status === 200) {
      tokens.push(res.json('accessToken'));
    }
  }
  if (tokens.length === 0) {
    throw new Error('토큰 풀이 비었다 — 시드/로그인 자격증명을 확인하라 (loadtest/seed-coupon-loadtest.sql)');
  }
  console.log(`토큰 풀 준비: ${tokens.length}/${MEMBER_COUNT} 로그인 성공, couponId=${COUPON_ID}`);
  return { tokens };
}

export default function (data) {
  // 시나리오 전역 반복 카운터로 토큰을 인덱싱 — 풀 크기까지는 회원이 겹치지 않는다.
  const idx = exec.scenario.iterationInTest % data.tokens.length;
  const token = data.tokens[idx];

  const res = http.post(`${BASE_URL}/api/v1/coupons/${COUPON_ID}/issue`, null, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Idempotency-Key': uuidv4(),
      'Content-Type': 'application/json',
    },
    tags: { phase: 'issue' },
    responseCallback: http.expectedStatuses(201, 409),
  });

  issueLatency.add(res.timings.duration);
  if (res.status === 201) {
    issued.add(1);
  } else if (res.status === 409) {
    rejected.add(1);
  }

  check(res, {
    '201 발급 또는 409 소진/중복 (5xx·기타 아님)': (r) => r.status === 201 || r.status === 409,
  });
}

export function handleSummary(data) {
  // setup() 토큰 풀(실제 JWT)을 비식별화한 뒤 요약을 기록한다 — 평문 토큰 유출 방지(#219).
  return redactedSummary(data, 'loadtest/summary.json');
}
