// 한정반 선착순 구매 API(POST /api/v1/limited-drops/{id}/purchase) 부하 테스트.
// 목표: 초과 판매 0건(NFR-02), p95 1초 이내(이 엔드포인트 기준. NFR-03 의 300ms 는 상품 목록 API 기준).
// 실행: k6 run scripts/k6/limited-purchase.js
// 환경변수: BASE_URL(기본 http://localhost:8080), VUS(기본 1000), STOCK(기본 100),
//           ADMIN_EMAIL/ADMIN_PASSWORD(기본 admin@groove.com/admin1234!),
//           OPEN_DELAY_SEC(기본 8), MEMBER_PASSWORD(기본 load1234!), RESULT_DIR(기본 scripts/k6/results)
// local 프로파일 시드(관리자 계정)가 필요하다. 상품/한정반/회원은 setup() 이 직접 만든다.
// 결과: 실행이 끝나면 RESULT_DIR 밑에 실행 시각 기준 JSON 요약 파일을 남긴다.

import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 1000);
const STOCK = Number(__ENV.STOCK || 100);
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@groove.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin1234!';
const OPEN_DELAY_SEC = Number(__ENV.OPEN_DELAY_SEC || 8);
const MEMBER_PASSWORD = __ENV.MEMBER_PASSWORD || 'load1234!';
const RESULT_DIR = __ENV.RESULT_DIR || 'scripts/k6/results';

const BATCH_SIZE = 20;
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// setup 은 signup 중복(409), 로그인/생성은 2xx 를 기대한다. 구매는 409(품절/중복)도 정상 응답이라
// http_req_failed 로 잡히지 않게 여기서 허용 상태코드를 전역으로 넓힌다.
http.setResponseCallback(http.expectedStatuses(201, 409, { min: 200, max: 299 }));

const purchaseSuccess = new Counter('purchase_success');
const purchaseSoldOut = new Counter('purchase_sold_out');
const purchaseAlready = new Counter('purchase_already');
const purchaseUnexpected = new Counter('purchase_unexpected');

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
    'http_req_duration{name:purchase}': ['p(95)<1000'],
    checks: ['rate>0.99'],
    purchase_success: [`count>=${STOCK}`, `count<=${STOCK}`],
    purchase_unexpected: ['count<1'],
  },
};

function pad(value) {
  return value < 10 ? `0${value}` : `${value}`;
}

// date(절대 시각) 를 offsetMinutes 로 표현되는 타임존의 벽시계 문자열(LocalDateTime, 초 단위)로 바꾼다.
// k6 실행 머신의 로컬 타임존과 무관하게 서버가 이해하는 시각 문자열을 만들기 위해 UTC 기준으로 계산한다.
function toLocalDateTimeString(date, offsetMinutes) {
  const shifted = new Date(date.getTime() + offsetMinutes * 60000);
  const year = shifted.getUTCFullYear();
  const month = pad(shifted.getUTCMonth() + 1);
  const day = pad(shifted.getUTCDate());
  const hour = pad(shifted.getUTCHours());
  const minute = pad(shifted.getUTCMinutes());
  const second = pad(shifted.getUTCSeconds());
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`;
}

// "...+09:00" / "...-05:30" 꼬리에서 오프셋(분)을 뽑아낸다.
function parseOffsetMinutes(offsetDateTime) {
  const matched = offsetDateTime.match(/([+-])(\d{2}):(\d{2})$/);
  if (!matched) {
    return 0;
  }
  const sign = matched[1] === '-' ? -1 : 1;
  return sign * (Number(matched[2]) * 60 + Number(matched[3]));
}

function chunk(array, size) {
  const chunks = [];
  for (let i = 0; i < array.length; i += size) {
    chunks.push(array.slice(i, i + size));
  }
  return chunks;
}

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
    title: `LIMITED-LOADTEST-${Date.now()}`,
    artistId,
    price: 45000,
    initialStock: STOCK,
  }), { headers: authHeader(adminToken), tags: { name: 'setup_product_create' } });

  if (res.status !== 201) {
    fail(`상품 생성에 실패했습니다. status=${res.status} body=${res.body}`);
  }
  return res.json('data.id');
}

function createDrop(adminToken, productId) {
  const now = new Date();
  const openAt = toLocalDateTimeString(new Date(now.getTime() + 60 * 60000), 540);
  const closeAt = toLocalDateTimeString(new Date(now.getTime() + 120 * 60000), 540);

  const res = http.post(`${BASE_URL}/api/v1/admin/limited-drops`, JSON.stringify({
    productId,
    totalQuantity: STOCK,
    perMemberLimit: 1,
    openAt,
    closeAt,
  }), { headers: authHeader(adminToken), tags: { name: 'setup_drop_create' } });

  if (res.status !== 201) {
    fail(`한정반 드롭 생성에 실패했습니다. status=${res.status} body=${res.body}`);
  }
  return res.json('data.id');
}

function getDropDetail(dropId) {
  const res = http.get(`${BASE_URL}/api/v1/limited-drops/${dropId}`, { tags: { name: 'setup_drop_detail' } });
  if (res.status !== 200) {
    fail(`한정반 상세 조회에 실패했습니다. status=${res.status} body=${res.body}`);
  }
  return res.json('data');
}

// openAt 을 서버 시각(serverTime) 기준 OPEN_DELAY_SEC 뒤로 다시 잡고, 오픈을 강제한다.
function rescheduleAndForceOpen(adminToken, dropId) {
  const detail = getDropDetail(dropId);
  const offsetMinutes = parseOffsetMinutes(detail.serverTime);
  const serverNow = new Date(detail.serverTime);
  const openAtDate = new Date(serverNow.getTime() + OPEN_DELAY_SEC * 1000);
  const closeAtDate = new Date(openAtDate.getTime() + 60 * 60000);

  const updateRes = http.patch(`${BASE_URL}/api/v1/admin/limited-drops/${dropId}`, JSON.stringify({
    openAt: toLocalDateTimeString(openAtDate, offsetMinutes),
    closeAt: toLocalDateTimeString(closeAtDate, offsetMinutes),
  }), { headers: authHeader(adminToken), tags: { name: 'setup_drop_update' } });

  if (updateRes.status !== 200) {
    fail(`한정반 드롭 오픈 시각 수정에 실패했습니다. status=${updateRes.status} body=${updateRes.body}`);
  }

  // 강제 오픈은 status/Redis 재고만 바꾸고 openAt 은 그대로 두므로, 실제 구매 가능은 openAt 도달 이후다.
  const openRes = http.patch(`${BASE_URL}/api/v1/admin/limited-drops/${dropId}/open`, null,
      { headers: authHeader(adminToken), tags: { name: 'setup_drop_open' } });
  if (openRes.status !== 200) {
    fail(`한정반 드롭 강제 오픈에 실패했습니다. status=${openRes.status} body=${openRes.body}`);
  }

  return openAtDate;
}

function signupMember(index) {
  return ['POST', `${BASE_URL}/api/v1/auth/signup`, JSON.stringify({
    email: `lt-${index}@groove.com`,
    password: MEMBER_PASSWORD,
    nickname: `lt${index}`,
  }), { headers: JSON_HEADERS, tags: { name: 'setup_signup' } }];
}

function loginMember(index) {
  return ['POST', `${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: `lt-${index}@groove.com`,
    password: MEMBER_PASSWORD,
  }), { headers: JSON_HEADERS, tags: { name: 'setup_login' } }];
}

function getAddresses(token) {
  return ['GET', `${BASE_URL}/api/v1/members/me/addresses`, null,
    { headers: authHeader(token), tags: { name: 'setup_address_get' } }];
}

function createAddress(token) {
  return ['POST', `${BASE_URL}/api/v1/members/me/addresses`, JSON.stringify({
    recipientName: '로드테스트',
    phone: '010-1234-5678',
    zipCode: '12345',
    address1: '부하테스트로 1',
    isDefault: true,
  }), { headers: authHeader(token), tags: { name: 'setup_address_create' } }];
}

// VUS 명 만큼 회원을 만들고 로그인·배송지까지 준비한다. BATCH_SIZE 씩 나눠 처리한다.
function createMembers() {
  const indexes = [];
  for (let i = 1; i <= VUS; i += 1) {
    indexes.push(i);
  }

  const users = [];
  chunk(indexes, BATCH_SIZE).forEach((batch) => {
    http.batch(batch.map(signupMember));

    const loginResponses = http.batch(batch.map(loginMember));
    const tokens = loginResponses.map((res, i) => {
      if (res.status !== 200) {
        fail(`회원 로그인에 실패했습니다. index=${batch[i]} status=${res.status} body=${res.body}`);
      }
      return res.json('data.accessToken');
    });

    const addressResponses = http.batch(tokens.map((token) => getAddresses(token)));
    const needsAddress = [];
    const addressIds = tokens.map((token, i) => {
      const addresses = addressResponses[i].json('data') || [];
      if (addresses.length > 0) {
        return addresses[0].id;
      }
      needsAddress.push(i);
      return null;
    });

    if (needsAddress.length > 0) {
      const createdResponses = http.batch(needsAddress.map((i) => createAddress(tokens[i])));
      needsAddress.forEach((i, position) => {
        const res = createdResponses[position];
        if (res.status !== 201) {
          fail(`배송지 생성에 실패했습니다. index=${batch[i]} status=${res.status} body=${res.body}`);
        }
        addressIds[i] = res.json('data.id');
      });
    }

    tokens.forEach((token, i) => {
      users.push({ token, addressId: addressIds[i] });
    });

    if (batch[0] % 200 === 1 || batch[0] === 1) {
      console.log(`회원 준비 진행: ${users.length}/${VUS}`);
    }
  });

  return users;
}

export function setup() {
  const adminToken = adminLogin();
  const artistId = firstArtistId();
  const productId = createProduct(adminToken, artistId);
  const dropId = createDrop(adminToken, productId);

  // 회원 준비가 끝난 뒤에 오픈 시각을 잡아야 모든 VU 가 오픈 직후에 몰린다.
  const users = createMembers();
  const openAtDate = rescheduleAndForceOpen(adminToken, dropId);

  const detail = getDropDetail(dropId);
  const serverNow = new Date(detail.serverTime);
  const waitMs = openAtDate.getTime() - serverNow.getTime() + 500;
  if (waitMs > 0) {
    sleep(waitMs / 1000);
  }

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

  purchaseUnexpected.add(1);
}

export function teardown(data) {
  const listRes = http.get(`${BASE_URL}/api/v1/admin/limited-drops?size=50`,
      { headers: authHeader(data.adminToken), tags: { name: 'teardown_list' } });
  const content = listRes.json('data.content') || [];
  const drop = content.find((item) => item.id === data.dropId);
  const soldCount = drop ? drop.soldCount : undefined;
  console.log(`teardown: dropId=${data.dropId} soldCount=${soldCount}`);

  const detail = getDropDetail(data.dropId);
  console.log(`teardown: remainingQuantity=${detail.remainingQuantity}`);

  if (soldCount !== data.stock) {
    fail(`초과/미달 판매가 발생했습니다. soldCount=${soldCount} stock=${data.stock}`);
  }
}

function timestamp() {
  const now = new Date();
  // 같은 분에 두 번 실행하면 앞 결과가 덮어써지므로 초까지 붙인다.
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
}

export function handleSummary(data) {
  const output = {};
  output['stdout'] = textSummary(data, { indent: ' ', enableColors: true });
  output[`${RESULT_DIR}/limited-${timestamp()}.json`] = JSON.stringify(data, null, 2);
  return output;
}
