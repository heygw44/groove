// 관리자 대시보드 진입 부하 테스트. 관리자로 로그인해 통계 4종(일별 매출/인기 상품/한정반 현황/요약)을
// 대시보드가 뜰 때처럼 동시에 호출한다. #316 의 사전 집계(sales_daily/sales_daily_product) 도입
// 전후로 같은 시나리오를 돌려 p95 를 비교하는 용도.
// 실행: k6 run scripts/k6/admin-dashboard.js
// 환경변수: BASE_URL(기본 http://localhost:8080), ADMIN_EMAIL/ADMIN_PASSWORD
//           (기본 admin@groove.com/admin1234!), RESULT_DIR(기본 scripts/k6/results)
// 합성 데이터가 있는 스키마(예: groove_load, backend/scripts/perf/seed-load-db.sh 로 생성)에 붙은
// 백엔드가 필요하다 — 소규모 local 시드 데이터로는 통계 API 가 빈 결과라 측정이 무의미하다.

import http from 'k6/http';
import { check, fail } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@groove.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin1234!';
const RESULT_DIR = __ENV.RESULT_DIR || 'scripts/k6/results';

const STAT_NAMES = ['daily-sales', 'popular-products', 'limited-drops', 'summary'];

const statThresholds = Object.fromEntries(
  STAT_NAMES.map((name) => [`http_req_duration{name:${name}}`, ['p(95)<300']]),
);

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    admin_dashboard: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    ...statThresholds,
  },
};

export function setup() {
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: ADMIN_EMAIL,
    password: ADMIN_PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup_admin_login' } });

  if (res.status !== 200) {
    fail(`관리자 로그인에 실패했습니다. status=${res.status} body=${res.body}`);
  }

  return { token: res.json('data.accessToken') };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };

  const responses = http.batch([
    ['GET', `${BASE_URL}/api/v1/admin/stats/daily-sales`, null, { headers, tags: { name: 'daily-sales' } }],
    ['GET', `${BASE_URL}/api/v1/admin/stats/popular-products`, null,
      { headers, tags: { name: 'popular-products' } }],
    ['GET', `${BASE_URL}/api/v1/admin/stats/limited-drops`, null, { headers, tags: { name: 'limited-drops' } }],
    ['GET', `${BASE_URL}/api/v1/admin/stats/summary`, null, { headers, tags: { name: 'summary' } }],
  ]);

  responses.forEach((res) => {
    check(res, {
      'status is 200': (r) => r.status === 200,
      'success is true': (r) => r.json('success') === true,
    });
  });
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
  output[`${RESULT_DIR}/admin-dashboard-${timestamp()}.json`] = JSON.stringify(data, null, 2);
  return output;
}
