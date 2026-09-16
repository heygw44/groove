// 배포(백엔드 컨테이너 재생성) 동안 진행 중(in-flight) 요청이 끝까지 처리되는지, 그리고 재기동 공백에서
// 거부·끊김이 몇 건 나는지를 잰다. 인스턴스가 하나뿐이라 재기동 공백의 새 요청은 어차피 거부되므로,
// 컨테이너 헬스체크·graceful shutdown·deploy-backend.sh 롤백이 "다운타임을 없앤다"는 뜻은 아니다.
//
// 실행 방법:
//   1. 목 서버 기동: PORT=18080 node scripts/k6/toss-mock.mjs
//   2. 백엔드를 목 서버를 보도록 compose full 프로파일로 기동:
//      TOSS_BASE_URL=http://host.docker.internal:18080 docker compose --profile full up -d --build
//   3. k6 시작: k6 run scripts/k6/deploy-drain.js
//   4. 도중에 배포를 흉내낸다: docker compose --profile full up -d --force-recreate backend
//
// 로컬에서는 docker 포트 포워딩 때문에 백엔드가 없는 순간의 새 연결이 "거부(refused)" 대신
// "끊김(reset)"으로 보일 수 있다. 판정할 때 두 Counter 를 합쳐서 본다.
//
// 환경변수: BASE_URL(기본 http://localhost:8080), DURATION(기본 120s), RATE(기본 20, product_list 초당 요청),
//           CONFIRM_ORDERS(기본 30, payment_confirm 로 미리 만들어 둘 주문 수 = 그 시나리오 VU 수),
//           ADMIN_EMAIL/ADMIN_PASSWORD(기본 admin@groove.com/admin1234!), MEMBER_PASSWORD(기본 load1234!),
//           MEMBER_EMAIL_PREFIX(기본 drain-), PRODUCT_TITLE_PREFIX(기본 DEPLOY-DRAIN-),
//           RESULT_DIR(기본 scripts/k6/results), RUN_LABEL(기본 빈 문자열)

import http from 'k6/http';
import exec from 'k6/execution';
import { fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const DURATION = __ENV.DURATION || '120s';
const RATE = Number(__ENV.RATE || 20);
const CONFIRM_ORDERS = Number(__ENV.CONFIRM_ORDERS || 30);
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@groove.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin1234!';
const MEMBER_PASSWORD = __ENV.MEMBER_PASSWORD || 'load1234!';
const MEMBER_EMAIL_PREFIX = __ENV.MEMBER_EMAIL_PREFIX || 'drain-';
const PRODUCT_TITLE_PREFIX = __ENV.PRODUCT_TITLE_PREFIX || 'DEPLOY-DRAIN-';
const RESULT_DIR = __ENV.RESULT_DIR || 'scripts/k6/results';
const RUN_LABEL = __ENV.RUN_LABEL || '';

const MEMBER_NICKNAME_PREFIX = MEMBER_EMAIL_PREFIX.replace(/[^a-zA-Z0-9]/g, '');
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const DURATION_SEC = parseDurationSeconds(DURATION);

// 배포 중 5xx·연결 거부·연결 끊김은 정상적으로 발생할 수 있는 관측 대상이라 http_req_failed 로 잡히지 않게
// 여기서 허용 상태코드를 전역으로 넓힌다. 판정은 아래 Counter 로 직접 분류한다.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 599 }));

function parseDurationSeconds(duration) {
  const match = duration.match(/^(\d+)s$/);
  if (!match) {
    fail(`DURATION 은 "120s" 형식만 지원한다: ${duration}`);
  }
  return Number(match[1]);
}

function makeCounters(prefix) {
  return {
    success: new Counter(`${prefix}_success`),
    serverError: new Counter(`${prefix}_server_error`),
    refused: new Counter(`${prefix}_refused`),
    reset: new Counter(`${prefix}_reset`),
    other: new Counter(`${prefix}_other`),
  };
}

const productListCounters = makeCounters('product_list');
const paymentConfirmCounters = makeCounters('payment_confirm');

// res.status 0(요청이 응답을 못 받음)은 res.error 로만 원인을 가른다.
function classify(res, counters, tags) {
  if (res.status >= 200 && res.status < 300) {
    counters.success.add(1, tags);
    return;
  }
  if (res.status >= 500) {
    counters.serverError.add(1, tags);
    return;
  }
  if (res.status === 0) {
    const error = (res.error || '').toLowerCase();
    if (error.includes('refused')) {
      counters.refused.add(1, tags);
      return;
    }
    if (/reset|eof|closed/.test(error)) {
      counters.reset.add(1, tags);
      return;
    }
  }
  counters.other.add(1, tags);
}

export const options = {
  setupTimeout: '3m',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    product_list: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(10, Math.ceil(RATE * 0.5)),
      maxVUs: RATE * 5,
      exec: 'productList',
    },
    payment_confirm: {
      executor: 'per-vu-iterations',
      vus: CONFIRM_ORDERS,
      iterations: 1,
      maxDuration: `${DURATION_SEC + 30}s`,
      exec: 'paymentConfirm',
    },
  },
};

function authHeader(token) {
  return { Authorization: `Bearer ${token}`, ...JSON_HEADERS };
}

function adminLogin() {
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: ADMIN_EMAIL,
    password: ADMIN_PASSWORD,
  }), { headers: JSON_HEADERS, tags: { name: 'setup_admin_login' } });

  if (res.status !== 200) {
    fail(`관리자 로그인에 실패했습니다. local 프로파일 시드 계정을 확인하세요. status=${res.status}`);
  }
  return res.json('data.accessToken');
}

function firstArtistId() {
  const res = http.get(`${BASE_URL}/api/v1/artists`, { tags: { name: 'setup_artists' } });
  const artists = res.json('data') || [];
  if (artists.length === 0) {
    fail('아티스트 목록이 비어 있습니다. local 프로파일 시드가 필요합니다.');
  }
  return artists[0].id;
}

function createProduct(adminToken, artistId) {
  const res = http.post(`${BASE_URL}/api/v1/admin/products`, JSON.stringify({
    title: `${PRODUCT_TITLE_PREFIX}${Date.now()}`,
    artistId,
    price: 45000,
    initialStock: CONFIRM_ORDERS,
    newAlbum: { title: `${PRODUCT_TITLE_PREFIX}ALBUM-${Date.now()}` },
  }), { headers: authHeader(adminToken), tags: { name: 'setup_product_create' } });

  if (res.status !== 201) {
    fail(`상품 생성에 실패했습니다. status=${res.status} body=${res.body}`);
  }
  return res.json('data.id');
}

function signupAndLogin(index) {
  const email = `${MEMBER_EMAIL_PREFIX}${index}@groove.com`;
  http.post(`${BASE_URL}/api/v1/auth/signup`, JSON.stringify({
    email,
    password: MEMBER_PASSWORD,
    nickname: `${MEMBER_NICKNAME_PREFIX}${index}`,
  }), { headers: JSON_HEADERS, tags: { name: 'setup_signup' } });

  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email,
    password: MEMBER_PASSWORD,
  }), { headers: JSON_HEADERS, tags: { name: 'setup_login' } });
  if (loginRes.status !== 200) {
    fail(`회원 로그인에 실패했습니다. index=${index} status=${loginRes.status} body=${loginRes.body}`);
  }
  return loginRes.json('data.accessToken');
}

function ensureAddress(token) {
  const listRes = http.get(`${BASE_URL}/api/v1/members/me/addresses`,
      { headers: authHeader(token), tags: { name: 'setup_address_get' } });
  const addresses = listRes.json('data') || [];
  if (addresses.length > 0) {
    return addresses[0].id;
  }

  const createRes = http.post(`${BASE_URL}/api/v1/members/me/addresses`, JSON.stringify({
    recipientName: '드레인테스트',
    phone: '010-1234-5678',
    zipCode: '12345',
    address1: '배포드레인로 1',
    isDefault: true,
  }), { headers: authHeader(token), tags: { name: 'setup_address_create' } });
  if (createRes.status !== 201) {
    fail(`배송지 생성에 실패했습니다. status=${createRes.status} body=${createRes.body}`);
  }
  return createRes.json('data.id');
}

// 회원 로그인 + 배송지 준비 후 PENDING 주문 하나를 만든다. payment_confirm 시나리오가 VU 당 하나씩 쓴다.
function createPendingOrder(token, productId) {
  const addressId = ensureAddress(token);
  const orderRes = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify({
    productId,
    quantity: 1,
    addressId,
  }), { headers: authHeader(token), tags: { name: 'setup_order_create' } });
  if (orderRes.status !== 201) {
    fail(`주문 생성에 실패했습니다. status=${orderRes.status} body=${orderRes.body}`);
  }
  const data = orderRes.json('data');
  return { token, orderNumber: data.orderNumber, amount: Math.round(Number(data.finalAmount)) };
}

export function setup() {
  const adminToken = adminLogin();
  const artistId = firstArtistId();
  const productId = createProduct(adminToken, artistId);

  const orders = [];
  for (let i = 1; i <= CONFIRM_ORDERS; i += 1) {
    const token = signupAndLogin(i);
    orders.push(createPendingOrder(token, productId));
    if (i % 10 === 0 || i === CONFIRM_ORDERS) {
      console.log(`주문 준비 진행: ${i}/${CONFIRM_ORDERS}`);
    }
  }

  console.log(`setup done: productId=${productId} orders=${orders.length}`);
  return { orders };
}

export function productList() {
  const res = http.get(`${BASE_URL}/api/v1/products`, { tags: { name: 'product_list' } });
  classify(res, productListCounters, { scenario: 'product_list' });
}

// payment_confirm VU 는 DURATION 전체에 고르게 퍼지도록 자기 순번만큼 대기한 뒤 딱 한 번 호출한다.
// __VU 는 동시에 도는 다른 시나리오(product_list)와 전역으로 공유돼 1..CONFIRM_ORDERS 범위를 보장하지 않으므로,
// 이 시나리오 안에서만 0-base 로 매겨지는 exec.scenario.iterationInTest 를 순번으로 쓴다.
export function paymentConfirm(data) {
  const index = Number(exec.scenario.iterationInTest);
  const order = data.orders[index];
  if (!order) {
    fail(`순번(${index}) 에 대응하는 주문이 없습니다. CONFIRM_ORDERS 를 확인하세요.`);
  }

  const delaySec = (index / Math.max(1, CONFIRM_ORDERS)) * DURATION_SEC;
  sleep(delaySec);

  const res = http.post(`${BASE_URL}/api/v1/payments/confirm`, JSON.stringify({
    paymentKey: `k6-drain-${__VU}-${Date.now()}`,
    orderId: order.orderNumber,
    amount: order.amount,
  }), { headers: authHeader(order.token), tags: { name: 'payment_confirm' } });

  classify(res, paymentConfirmCounters, { scenario: 'payment_confirm' });
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
  const fileName = RUN_LABEL
    ? `deploy-drain-${RUN_LABEL}-${timestamp()}.json`
    : `deploy-drain-${timestamp()}.json`;
  output[`${RESULT_DIR}/${fileName}`] = JSON.stringify(data, null, 2);
  return output;
}
