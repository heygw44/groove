// 재고 복원 경로 lost-update 재현/검증 부하 테스트 (#234) — 같은 앨범 행에서 주문 생성(place, −1)과
// 주문 취소(cancel, +1, 복원 경로)를 동시에 인터리브해 place↔복원 lost-update 를 HTTP 부하 계층에서 측정한다.
//
// Before(현행 main 기준 측정 시): 복원이 락 없는 read-modify-write 면 place 와 복원이 같은 album 행을 두고
//   경합해 lost-update 가 누적된다(StockRestoreConcurrencyTest 의 인프로세스 baseline 대응).
// After(#234 원자적 가산 UPDATE 적용 후): restoreStock 가 DB 한 문장에서 stock=stock+delta 를 원자적으로
//   적용하고 place 의 SELECT ... FOR UPDATE 와 같은 행에서 직렬화되어 lost-update 0 이어야 한다.
// 백엔드는 수정하지 않고, 같은 스크립트를 Before/After 코드에서 각각 돌려 비교한다.
//
// lost-update 판정(handleSummary 가 자동 계산):
//   기대 최종 재고 expected = stockAfterSeed − place_created(201) + cancel_ok(200)
//   lost = expected − finalStock. 원자적이면 lost == 0, 락 없는 RMW 면 lost != 0(또는 5xx 경합).
//
// 선행: 앱 기동 + scripts/seed.sh (회원 80 · admin 1, 공유 비번 Test1234!).
//       setup 로그인/시드 버스트가 throttle 되지 않도록 AUTH_RATE_LIMIT_LOGIN_CAPACITY 를 크게 주입한다.
//       타깃은 SELLING 앨범 1건(한정반 권장): SELECT id FROM album WHERE status='SELLING' ORDER BY id LIMIT 1;
// 실행: k6 run -e TARGET_ALBUM_ID=<id> -e SEED_ORDERS=100 -e PEAK_VUS=100 loadtest/stock-restore.js

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Gauge, Trend } from 'k6/metrics';
import { buildTokenPool, seedEmail } from './lib/auth.js';
import { buildSingleAlbumOrder } from './lib/orders.js';
import { redactedSummary } from './lib/summary.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MEMBER_COUNT = Number(__ENV.MEMBER_COUNT || 80);
const PASSWORD = __ENV.PASSWORD || 'Test1234!';
const EMAIL_PREFIX = __ENV.EMAIL_PREFIX || 'loadtest';
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || '@groove.test';
const TARGET_ALBUM_ID = Number(__ENV.TARGET_ALBUM_ID || 1); // place·cancel 를 몰아칠 단일 앨범 id
const SEED_ORDERS = Number(__ENV.SEED_ORDERS || 100); // 동시 구간에서 취소할 선행 PENDING 주문 수 = 복원(+1) 횟수
const PEAK_VUS = Number(__ENV.PEAK_VUS || 100); // 동시 VU
// 최악 인터리빙(모든 place 가 cancel 보다 먼저)에서도 재고부족이 안 나도록 INITIAL_STOCK ≥ 2·SEED_ORDERS.
const INITIAL_STOCK = Number(__ENV.INITIAL_STOCK || Math.max(300, SEED_ORDERS * 3));
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'loadtest-admin@groove.test';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || PASSWORD;

const placeCreated = new Counter('place_created'); // 201 신규 주문 생성(−1)
const placeFailed = new Counter('place_failed'); // place 의 409/422/5xx
const cancelOk = new Counter('cancel_ok'); // 200 주문 취소 = 재고 복원(+1)
const cancelFailed = new Counter('cancel_failed'); // cancel 의 4xx/5xx
const opLatency = new Trend('op_latency', true); // place/cancel 성공 응답 지연
const stockAfterSeedGauge = new Gauge('stock_after_seed'); // setup 종료 시 재고(= INITIAL_STOCK − SEED_ORDERS)
const finalStockGauge = new Gauge('final_stock'); // teardown 최종 재고 → handleSummary lost-update 판정

export const options = {
  scenarios: {
    place_cancel_interleave: {
      // 고정 횟수(place SEED + cancel SEED = 2·SEED)를 다수 VU 로 동시에 — 각 cancel 은 고유한 선행 주문을 친다.
      executor: 'shared-iterations',
      vus: PEAK_VUS,
      iterations: 2 * SEED_ORDERS,
      maxDuration: '60s',
    },
  },
  thresholds: {
    // Before/After 비교 측정이라 실패를 게이트하지 않는다. 유일한 하드 게이트: place·cancel 이 최소 1건씩은 성공.
    place_created: ['count>0'],
    cancel_ok: ['count>0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const email = seedEmail(EMAIL_PREFIX, 3, EMAIL_DOMAIN);

function loginAdmin() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { phase: 'setup-login' } },
  );
  if (res.status !== 200 && res.status !== 201) {
    throw new Error(`admin 로그인 실패(${res.status}) — 시드/자격증명 확인(${ADMIN_EMAIL}). scripts/seed.sh`);
  }
  const token = res.json('accessToken');
  if (!token) {
    throw new Error('admin 로그인 응답에 accessToken 이 없다 — 인증 응답 스키마 확인');
  }
  return token;
}

// 타깃 앨범 재고를 INITIAL_STOCK 으로 정확히 리셋(PATCH /stock 은 delta). SELLING 아니면 중단.
function resetTargetStock(adminToken) {
  const detail = http.get(`${BASE_URL}/api/v1/albums/${TARGET_ALBUM_ID}`, { tags: { phase: 'setup-reset' } });
  if (detail.status !== 200) {
    throw new Error(`타깃 앨범 조회 실패(${detail.status}) — TARGET_ALBUM_ID=${TARGET_ALBUM_ID} 존재 확인`);
  }
  if (detail.json('status') !== 'SELLING') {
    throw new Error(`타깃 앨범(id=${TARGET_ALBUM_ID})이 SELLING 이 아니다(status=${detail.json('status')})`);
  }
  const delta = INITIAL_STOCK - detail.json('stock');
  if (delta !== 0) {
    const patched = http.patch(
      `${BASE_URL}/api/v1/admin/albums/${TARGET_ALBUM_ID}/stock`,
      JSON.stringify({ delta }),
      { headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' }, tags: { phase: 'setup-reset' } },
    );
    if (patched.status !== 200 || patched.json('stock') !== INITIAL_STOCK) {
      throw new Error(`재고 리셋 실패(status=${patched.status}, stock=${patched.json('stock')}) — admin 권한 확인`);
    }
  }
  console.log(`타깃 앨범 id=${TARGET_ALBUM_ID} 재고 리셋: → ${INITIAL_STOCK} ('${detail.json('title')}')`);
}

// 동시 구간에서 취소할 선행 PENDING 주문 SEED_ORDERS 건을 만든다(각 −1). 취소는 본인만 가능하므로
// {orderNumber, tokenIndex} 페어로 소유 토큰을 기록한다.
function seedPendingOrders(tokens, runTag) {
  const seeded = [];
  const body = buildSingleAlbumOrder(TARGET_ALBUM_ID, 1);
  for (let j = 0; j < SEED_ORDERS; j++) {
    const tokenIndex = j % tokens.length;
    const res = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify(body), {
      // 주문 생성은 Idempotency-Key 필수 — 시드 주문마다 고유 키
      headers: { Authorization: `Bearer ${tokens[tokenIndex]}`, 'Content-Type': 'application/json', 'Idempotency-Key': `sr-seed-${runTag}-${j}` },
      tags: { phase: 'setup-seed' },
    });
    if (res.status === 201 && res.json('orderNumber')) {
      seeded.push({ orderNumber: res.json('orderNumber'), tokenIndex });
    }
  }
  if (seeded.length < SEED_ORDERS) {
    throw new Error(`선행 주문 시드 부족: ${seeded.length}/${SEED_ORDERS} — 재고/토큰 풀 확인`);
  }
  return seeded;
}

export function setup() {
  const adminToken = loginAdmin();
  resetTargetStock(adminToken);
  const tokens = buildTokenPool({ baseUrl: BASE_URL, count: MEMBER_COUNT, password: PASSWORD, email });
  const runTag = `${Date.now()}`;
  const seeded = seedPendingOrders(tokens, runTag);
  const stockAfterSeed = http.get(`${BASE_URL}/api/v1/albums/${TARGET_ALBUM_ID}`).json('stock');
  stockAfterSeedGauge.add(stockAfterSeed); // == INITIAL_STOCK − SEED_ORDERS
  console.log(`시드 완료: 선행 주문 ${seeded.length}건, 시드후 재고 ${stockAfterSeed}`);
  return { tokens, seeded, stockAfterSeed, runTag };
}

export default function (data) {
  // 전역 단조 카운터 — 짝수 = 신규 place(−1), 홀수 = 선행 주문 cancel(+1). 두 작업이 같은 album 행에서 동시 경합.
  const n = exec.scenario.iterationInTest;
  if (n % 2 === 0) {
    const token = data.tokens[(n / 2) % data.tokens.length];
    const res = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify(buildSingleAlbumOrder(TARGET_ALBUM_ID, 1)), {
      // 주문 생성은 Idempotency-Key 필수 — place 마다 고유 키
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': `sr-place-${data.runTag}-${n}` },
      tags: { phase: 'place' },
      responseCallback: http.expectedStatuses(201, 409, 422),
    });
    if (res.status === 201) {
      placeCreated.add(1);
      opLatency.add(res.timings.duration);
    } else {
      placeFailed.add(1);
    }
    check(res, { 'place 201 또는 409/422': (r) => [201, 409, 422].includes(r.status) });
  } else {
    const order = data.seeded[(n - 1) / 2];
    const token = data.tokens[order.tokenIndex];
    const res = http.post(`${BASE_URL}/api/v1/orders/${order.orderNumber}/cancel`, JSON.stringify({ reason: '부하테스트' }), {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      tags: { phase: 'cancel' },
      responseCallback: http.expectedStatuses(200, 409),
    });
    if (res.status === 200) {
      cancelOk.add(1);
      opLatency.add(res.timings.duration);
    } else {
      cancelFailed.add(1);
    }
    check(res, { 'cancel 200 또는 409': (r) => [200, 409].includes(r.status) });
  }
}

export function teardown() {
  const detail = http.get(`${BASE_URL}/api/v1/albums/${TARGET_ALBUM_ID}`);
  if (detail.status !== 200) {
    console.warn(`최종 재고 조회 실패(${detail.status}) — 수동 확인: SELECT stock FROM album WHERE id=${TARGET_ALBUM_ID};`);
    return;
  }
  finalStockGauge.add(detail.json('stock'));
  console.log(`[#234 복원] finalStock=${detail.json('stock')}`);
}

export function handleSummary(data) {
  const placeCreatedN = data.metrics.place_created ? data.metrics.place_created.values.count : 0;
  const placeFailedN = data.metrics.place_failed ? data.metrics.place_failed.values.count : 0;
  const cancelOkN = data.metrics.cancel_ok ? data.metrics.cancel_ok.values.count : 0;
  const cancelFailedN = data.metrics.cancel_failed ? data.metrics.cancel_failed.values.count : 0;
  const stockAfterSeed = data.metrics.stock_after_seed ? data.metrics.stock_after_seed.values.value : null;
  const finalStock = data.metrics.final_stock ? data.metrics.final_stock.values.value : null;

  let verdict;
  if (stockAfterSeed === null || finalStock === null) {
    verdict = '재고 게이지 누락 — 수동 확인 필요(stockAfterSeed/finalStock)';
  } else {
    const expected = stockAfterSeed - placeCreatedN + cancelOkN; // lost-update 0 이면 finalStock 과 정확히 일치
    const lost = expected - finalStock;
    verdict =
      lost === 0
        ? `lost-update 0 — finalStock(${finalStock}) == expected(${expected}) [stockAfterSeed ${stockAfterSeed} − place ${placeCreatedN} + cancel ${cancelOkN}]`
        : `lost-update ${lost}건 — finalStock(${finalStock}) != expected(${expected}) [stockAfterSeed ${stockAfterSeed} − place ${placeCreatedN} + cancel ${cancelOkN}]`;
  }
  console.log(
    `[#234 복원] place_created=${placeCreatedN}, place_failed=${placeFailedN}, cancel_ok=${cancelOkN}, ` +
      `cancel_failed=${cancelFailedN} → ${verdict}`,
  );
  return redactedSummary(data, 'loadtest/stock-restore-summary.json');
}
