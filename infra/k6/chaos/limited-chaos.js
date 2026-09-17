// 한정반 카오스 부하 테스트(#401). limited-purchase.js 와 달리 러시를 수십 초에 걸쳐 이어가면서
// infra/k6/chaos/run.sh 가 그 도중에 장애(Redis 재시작/키 삭제/앱 강제종료)를 주입한다.
// 목표는 초과판매가 아니라 "장애 중 응답 분포"와 "장애 복구·대사 이후 남은 재고가 정상 판매되는가"를 보는 것.
// 단독 실행도 가능하다: k6 run infra/k6/chaos/limited-chaos.js (장애 주입은 run.sh 가 별도로 한다)
//
// 환경변수(기본값):
//   BASE_URL            http://localhost:8080
//   MEMBERS             1000   러시 도중 순환할 회원 수
//   STOCK                500   상품 재고 / 드롭 totalQuantity
//   RATE                150    러시 구간 rps. 드롭 행 락 때문에 커밋은 초당 100건 안팎이라 이보다 높아야 선점→커밋 사이 요청이 쌓인다
//   RUSH_DURATION         20s  러시 구간 길이
//   TAIL_RATE             5    테일 구간 목표 rps(복구 후 잔여 재고를 계속 사려는 트래픽)
//   TAIL_DURATION        120s  테일 구간 길이
//   PRE_VUS              100   ramping-arrival-rate preAllocatedVUs
//   MAX_VUS              400   ramping-arrival-rate maxVUs
//   OPEN_DELAY_SEC         8   드롭 강제 오픈까지 대기
//   ADMIN_EMAIL/ADMIN_PASSWORD  admin@groove.com / admin1234!
//   MEMBER_PASSWORD       load1234!
//   MEMBER_EMAIL_PREFIX   chaos-
//   PRODUCT_TITLE_PREFIX  LIMITED-CHAOS-
//   RESULT_DIR            infra/k6/results
//   RUN_LABEL             (빈 문자열)
//   SETUP_BATCH_SIZE      20

import http from 'k6/http';
import { fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import {
  adminLogin, firstArtistId, createProduct, createDrop, getDropDetail,
  rescheduleAndForceOpen, createMembers, authHeader, timestamp,
} from '../lib/limited-setup.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MEMBERS = Number(__ENV.MEMBERS || 1000);
const STOCK = Number(__ENV.STOCK || 500);
const RATE = Number(__ENV.RATE || 150);
const RUSH_DURATION = __ENV.RUSH_DURATION || '20s';
const TAIL_RATE = Number(__ENV.TAIL_RATE || 5);
const TAIL_DURATION = __ENV.TAIL_DURATION || '120s';
const PRE_VUS = Number(__ENV.PRE_VUS || 100);
const MAX_VUS = Number(__ENV.MAX_VUS || 400);
const OPEN_DELAY_SEC = Number(__ENV.OPEN_DELAY_SEC || 8);
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@groove.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin1234!';
const MEMBER_PASSWORD = __ENV.MEMBER_PASSWORD || 'load1234!';
const MEMBER_EMAIL_PREFIX = __ENV.MEMBER_EMAIL_PREFIX || 'chaos-';
const PRODUCT_TITLE_PREFIX = __ENV.PRODUCT_TITLE_PREFIX || 'LIMITED-CHAOS-';
const RESULT_DIR = __ENV.RESULT_DIR || 'infra/k6/results';
const RUN_LABEL = __ENV.RUN_LABEL || '';
const SETUP_BATCH_SIZE = Number(__ENV.SETUP_BATCH_SIZE || 20);

// 닉네임 검증(2~20자)에 걸리지 않도록 접두사에서 영숫자만 남긴다. lt- 회원과 섞이지 않도록 chaos- 를 쓴다.
const MEMBER_NICKNAME_PREFIX = MEMBER_EMAIL_PREFIX.replace(/[^a-zA-Z0-9]/g, '');

const BATCH_SIZE = Math.max(20, SETUP_BATCH_SIZE);

// 400 LIMITED_NOT_OPEN 은 카오스에서 기대되는 응답(재적재/대사 창)이라 http_req_failed 에서 뺀다.
// 503/5xx/0 은 실패로 남긴다.
http.setResponseCallback(http.expectedStatuses(201, 400, 409, { min: 200, max: 299 }));

const purchaseOutcome = new Counter('purchase_outcome');
const purchaseSuccess = new Counter('purchase_success');
const purchaseSoldOut = new Counter('purchase_sold_out');
const purchaseAlready = new Counter('purchase_already');
const purchaseNotOpen = new Counter('purchase_not_open');
const purchaseBusy = new Counter('purchase_busy');
const purchaseServerError = new Counter('purchase_server_error');
const purchaseConnError = new Counter('purchase_conn_error');
const purchaseOther = new Counter('purchase_other');

export const options = {
  setupTimeout: '10m',
  batch: BATCH_SIZE,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    limited_rush: {
      executor: 'ramping-arrival-rate',
      // startRate 를 안 주면 0rps 에서 램프업해 T+2초 장애 시점에 요청이 거의 없다.
      startRate: RATE,
      timeUnit: '1s',
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
      stages: [
        { target: RATE, duration: RUSH_DURATION },
        { target: TAIL_RATE, duration: '1s' },
        { target: TAIL_RATE, duration: TAIL_DURATION },
      ],
      gracefulStop: '30s',
    },
  },
  thresholds: {
    // 초과판매만 실패 조건으로 둔다. 장애 창에서 p95/checks 가 깨지는 건 당연하다.
    purchase_success: [`count<=${STOCK}`],
  },
};

export function setup() {
  const adminToken = adminLogin(BASE_URL, ADMIN_EMAIL, ADMIN_PASSWORD);
  const artistId = firstArtistId(BASE_URL);
  const productId = createProduct(BASE_URL, adminToken, artistId, STOCK, PRODUCT_TITLE_PREFIX);
  const dropId = createDrop(BASE_URL, adminToken, productId, STOCK);

  const users = createMembers({
    baseUrl: BASE_URL,
    memberCount: MEMBERS,
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
  console.log(`CHAOS_RUSH_START ms=${Date.now()}`);
  return { dropId, productId, stock: STOCK, users, adminToken };
}

export default function (data) {
  // 회원이 한 바퀴 돌면 이미 산 회원은 409 ALREADY, 못 산 회원은 재시도한다(복구 뒤 남은 재고를 산다).
  const user = data.users[exec.scenario.iterationInTest % MEMBERS];
  if (!user) {
    fail(`회원(index=${exec.scenario.iterationInTest % MEMBERS}) 이 준비되지 않았습니다.`);
  }

  const res = http.post(`${BASE_URL}/api/v1/limited-drops/${data.dropId}/purchase`, JSON.stringify({
    addressId: user.addressId,
  }), { headers: authHeader(user.token), tags: { name: 'purchase' } });

  const outcome = classify(res);
  purchaseOutcome.add(1, { outcome });
}

function classify(res) {
  if (res.status === 201) {
    purchaseSuccess.add(1);
    return 'success';
  }

  if (res.status === 409) {
    const code = res.json('error.code');
    if (code === 'LIMITED_SOLD_OUT') {
      purchaseSoldOut.add(1);
      return 'sold_out';
    }
    if (code === 'LIMITED_ALREADY_PURCHASED') {
      purchaseAlready.add(1);
      return 'already';
    }
    purchaseOther.add(1);
    return 'other';
  }

  if (res.status === 400) {
    const code = res.json('error.code');
    if (code === 'LIMITED_NOT_OPEN') {
      purchaseNotOpen.add(1);
      return 'not_open';
    }
    purchaseOther.add(1);
    return 'other';
  }

  if (res.status === 503) {
    purchaseBusy.add(1);
    return 'busy';
  }

  // status 0 은 연결 실패/타임아웃.
  if (res.status === 0) {
    purchaseConnError.add(1);
    return 'conn_error';
  }

  if (res.status >= 500) {
    purchaseServerError.add(1);
    return 'server_error';
  }

  purchaseOther.add(1);
  return 'other';
}

export function teardown(data) {
  const listRes = http.get(`${BASE_URL}/api/v1/admin/limited-drops?size=50`,
      { headers: authHeader(data.adminToken), tags: { name: 'teardown_list' } });
  const content = listRes.json('data.content') || [];
  const drop = content.find((item) => item.id === data.dropId);
  const soldCount = drop ? drop.soldCount : undefined;

  const detail = getDropDetail(BASE_URL, data.dropId);
  console.log(`teardown: soldCount=${soldCount} remaining=${detail.remainingQuantity}`);
}

export function handleSummary(data) {
  const output = {};
  output['stdout'] = textSummary(data, { indent: ' ', enableColors: true });
  const fileName = RUN_LABEL
    ? `chaos-${RUN_LABEL}-${timestamp()}.json`
    : `chaos-${timestamp()}.json`;
  output[`${RESULT_DIR}/${fileName}`] = JSON.stringify(data, null, 2);
  return output;
}
