// 한정반 선착순 구매 API(POST /api/v1/limited-drops/{id}/purchase) 부하 테스트.
// 목표: 초과 판매 0건(NFR-02), p95 1초 이내(이 엔드포인트 기준. NFR-03 의 300ms 는 상품 목록 API 기준).
// 실행: k6 run infra/k6/limited-purchase.js
// 환경변수: BASE_URL(기본 http://localhost:8080), VUS(기본 1000), STOCK(기본 100),
//           ADMIN_EMAIL/ADMIN_PASSWORD(기본 admin@groove.com/admin1234!),
//           OPEN_DELAY_SEC(기본 8), MEMBER_PASSWORD(기본 load1234!), RESULT_DIR(기본 infra/k6/results),
//           MEMBER_EMAIL_PREFIX(기본 lt-), PRODUCT_TITLE_PREFIX(기본 LIMITED-LOADTEST-),
//           RUN_LABEL(기본 빈 문자열), SETUP_BATCH_SIZE(기본 20),
//           P95_MS(기본 1000), CHECK_RATE(기본 0.99)
// local 프로파일 시드(관리자 계정)가 필요하다. 상품/한정반/회원은 setup() 이 직접 만든다.
// 결과: 실행이 끝나면 RESULT_DIR 밑에 실행 시각 기준 JSON 요약 파일을 남긴다.

import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import {
  adminLogin, firstArtistId, createProduct, createDrop, getDropDetail,
  rescheduleAndForceOpen, createMembers, authHeader, timestamp, upstreamTag, upstreamSummaryLine,
} from './lib/limited-setup.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 1000);
const STOCK = Number(__ENV.STOCK || 100);
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@groove.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin1234!';
const OPEN_DELAY_SEC = Number(__ENV.OPEN_DELAY_SEC || 8);
const MEMBER_PASSWORD = __ENV.MEMBER_PASSWORD || 'load1234!';
const RESULT_DIR = __ENV.RESULT_DIR || 'infra/k6/results';
const MEMBER_EMAIL_PREFIX = __ENV.MEMBER_EMAIL_PREFIX || 'lt-';
const PRODUCT_TITLE_PREFIX = __ENV.PRODUCT_TITLE_PREFIX || 'LIMITED-LOADTEST-';
const RUN_LABEL = __ENV.RUN_LABEL || '';
const SETUP_BATCH_SIZE = Number(__ENV.SETUP_BATCH_SIZE || 20);
const P95_MS = Number(__ENV.P95_MS || 1000);
const CHECK_RATE = Number(__ENV.CHECK_RATE || 0.99);

// 닉네임 검증(2~20자)에 걸리지 않도록 접두사에서 영숫자만 남긴다.
const MEMBER_NICKNAME_PREFIX = MEMBER_EMAIL_PREFIX.replace(/[^a-zA-Z0-9]/g, '');

// 운영은 t3.micro 라 회원 준비 단계의 BCrypt 동시성(SETUP_BATCH_SIZE)을 요청 동시성(options.batch)과 분리한다.
const BATCH_SIZE = Math.max(20, SETUP_BATCH_SIZE);

// setup 은 signup 중복(409), 로그인/생성은 2xx 를 기대한다. 구매는 409(품절/중복)도 정상 응답이라
// http_req_failed 로 잡히지 않게 여기서 허용 상태코드를 전역으로 넓힌다.
http.setResponseCallback(http.expectedStatuses(201, 409, { min: 200, max: 299 }));

const purchaseSuccess = new Counter('purchase_success');
const purchaseSoldOut = new Counter('purchase_sold_out');
const purchaseAlready = new Counter('purchase_already');
const purchaseUnexpected = new Counter('purchase_unexpected');
const purchaseServerError = new Counter('purchase_server_error');
// scale 프로필(Nginx + 앱 2인스턴스)에서 요청이 두 인스턴스로 실제로 갈라지는지 보기 위한 태그 카운터.
const purchaseByUpstream = new Counter('purchase_by_upstream');

export const options = {
  setupTimeout: '10m',
  batch: BATCH_SIZE,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    limited_rush: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '2m',
      gracefulStop: '30s',
    },
  },
  thresholds: {
    'http_req_duration{name:purchase}': [`p(95)<${P95_MS}`],
    checks: [`rate>${CHECK_RATE}`],
    purchase_success: [`count>=${STOCK}`, `count<=${STOCK}`],
    purchase_unexpected: ['count<1'],
  },
};

export function setup() {
  const adminToken = adminLogin(BASE_URL, ADMIN_EMAIL, ADMIN_PASSWORD);
  const artistId = firstArtistId(BASE_URL);
  const productId = createProduct(BASE_URL, adminToken, artistId, STOCK, PRODUCT_TITLE_PREFIX);
  const dropId = createDrop(BASE_URL, adminToken, productId, STOCK);

  // 회원 준비가 끝난 뒤에 오픈 시각을 잡아야 모든 VU 가 오픈 직후에 몰린다.
  const users = createMembers({
    baseUrl: BASE_URL,
    memberCount: VUS,
    memberEmailPrefix: MEMBER_EMAIL_PREFIX,
    memberNicknamePrefix: MEMBER_NICKNAME_PREFIX,
    memberPassword: MEMBER_PASSWORD,
    batchSize: SETUP_BATCH_SIZE,
  });
  const openAtDate = rescheduleAndForceOpen(BASE_URL, adminToken, dropId, OPEN_DELAY_SEC);

  const detail = getDropDetail(BASE_URL, dropId);
  const serverNow = new Date(detail.serverTime);
  const waitMs = openAtDate.getTime() - serverNow.getTime() + 500;
  if (waitMs > 0) {
    sleep(waitMs / 1000);
  }

  console.log(`setup done: dropId=${dropId} productId=${productId}`);
  return { dropId, productId, stock: STOCK, users, adminToken };
}

export default function (data) {
  const user = data.users[__VU - 1];
  if (!user) {
    fail(`VU(${__VU}) 에 대응하는 사용자가 없습니다. VUS 와 users 배열 크기를 확인하세요.`);
  }

  const res = http.post(`${BASE_URL}/api/v1/limited-drops/${data.dropId}/purchase`, JSON.stringify({
    addressId: user.addressId,
  }), { headers: authHeader(user.token), tags: { name: 'purchase' } });

  check(res, { '201 or 409': (r) => r.status === 201 || r.status === 409 });
  purchaseByUpstream.add(1, { upstream: upstreamTag(res) });

  if (res.status === 201) {
    purchaseSuccess.add(1);
    return;
  }

  if (res.status === 409) {
    const code = res.json('error.code');
    if (code === 'LIMITED_SOLD_OUT') {
      purchaseSoldOut.add(1);
    } else if (code === 'LIMITED_ALREADY_PURCHASED') {
      purchaseAlready.add(1);
    } else {
      purchaseUnexpected.add(1);
    }
    return;
  }

  // status 0 은 연결 실패/타임아웃. 5xx 와 함께 서버/인프라 문제로 따로 센다.
  if (res.status === 0 || res.status >= 500) {
    purchaseServerError.add(1);
    return;
  }

  purchaseUnexpected.add(1);
}

export function teardown(data) {
  const listRes = http.get(`${BASE_URL}/api/v1/admin/limited-drops?size=50`,
      { headers: authHeader(data.adminToken), tags: { name: 'teardown_list' } });
  const content = listRes.json('data.content') || [];
  const drop = content.find((item) => item.id === data.dropId);
  const soldCount = drop ? drop.soldCount : undefined;
  console.log(`teardown: dropId=${data.dropId} productId=${data.productId} soldCount=${soldCount}`);

  const detail = getDropDetail(BASE_URL, data.dropId);
  console.log(`teardown: remainingQuantity=${detail.remainingQuantity}`);

  if (soldCount !== data.stock) {
    fail(`초과/미달 판매가 발생했습니다. soldCount=${soldCount} stock=${data.stock}`);
  }
}

export function handleSummary(data) {
  const output = {};
  output['stdout'] = textSummary(data, { indent: ' ', enableColors: true }) + upstreamSummaryLine(data);
  const fileName = RUN_LABEL
    ? `limited-${RUN_LABEL}-${timestamp()}.json`
    : `limited-${timestamp()}.json`;
  output[`${RESULT_DIR}/${fileName}`] = JSON.stringify(data, null, 2);
  return output;
}
