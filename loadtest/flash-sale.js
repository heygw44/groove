// 한정반 동시 주문 오버셀 재현 부하 테스트 (#194) — 재고 100인 단일 앨범에 동시 주문을 몰아
// 오버셀(oversell)을 HTTP 부하 계층에서 재현한다(동시성 개선의 Before).
//
// 현재 OrderService.place() 의 재고 차감은 락 없는 read-modify-write 다: loadPurchasable() 가 SELECT 로
// stock 을 읽고, decreaseStock() 이 인메모리로 검사·adjustStock(-qty) 한 뒤, 트랜잭션 커밋 시점에 dirty-check
// 가 UPDATE 를 flush 한다. 이 구간에 DB 락도 앱 동기화도 없어 lost-update 가 발생한다(락 없는 baseline,
// OversellingBaselineTest 가 단위테스트로 입증). 본 스크립트는 이를 부하로 재현하고 결과 JSON 을 보존한다.
// 백엔드는 수정하지 않는다. 비관적 락 적용 후 동일 스크립트로 After 측정한다.
//
// 오버셀 판정(handleSummary 가 자동 계산):
//   (1) order_created(201) > INITIAL_STOCK(100)                 — 재고보다 많이 팔린 주문(이슈 DoD)
//   (2) order_created > consumed(= INITIAL_STOCK - finalStock)  — lost-update(주문 수 > 실제 재고 감소량)
// (1)·(2) 중 하나라도 성립하면 오버셀. 락 경합으로 성공 응답이 100 미만에 머물러도 (2)가 잡아낸다(베이스라인
// 단위테스트의 success 27~34 < 100 참고). handleSummary 안에서는 http 호출이 불가하므로, teardown 이 최종 재고를
// 읽어 final_stock Gauge 로 emit 하고 handleSummary 가 created 와 합산해 단일 이진 판정을 출력한다.
//
// 선행: 앱 기동 + scripts/seed.sh 로 메인 시드 적용(한정반 40 · 회원 80 · admin 1, 공유 비번 Test1234!).
//       setup 의 로그인 버스트가 throttle 되지 않도록 AUTH_RATE_LIMIT_LOGIN_CAPACITY 를 크게 주입한다.
//       타깃은 SELLING 상태의 한정반 1건: SELECT id FROM album WHERE is_limited=1 AND status='SELLING' ORDER BY id LIMIT 1;
// 실행: k6 run -e TARGET_ALBUM_ID=<id> -e PEAK_VUS=1000 loadtest/flash-sale.js   (부하 레벨 100/500/1000 반복)

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
const TARGET_ALBUM_ID = Number(__ENV.TARGET_ALBUM_ID || 1); // 동시 쇄도시킬 한정반 앨범 id
const PEAK_VUS = Number(__ENV.PEAK_VUS || 1000); // 부하 레벨(피크 동시 VU) — 100/500/1000
const INITIAL_STOCK = Number(__ENV.INITIAL_STOCK || 100); // setup 이 타깃 앨범을 이 값으로 리셋
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'loadtest-admin@groove.test'; // 재고 리셋용 admin 계정
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || PASSWORD;

const orderCreated = new Counter('order_created'); // 201 주문 생성 성공(= 영속화된 주문 수)
const orderSoldout = new Counter('order_soldout'); // 409 재고부족(소진 후 정상 거절)
const orderFailed = new Counter('order_failed'); // 그 외(422·5xx·락경합 등 — Before 의 실패 양상)
const orderLatency = new Trend('order_latency', true); // 생성 성공(201) 주문 지연만 집계
const finalStockGauge = new Gauge('final_stock'); // teardown 이 최종 재고 기록 → handleSummary 가 lost-update 자동 판정

export const options = {
  scenarios: {
    flash_sale: {
      executor: 'ramping-vus',
      // 점진 램프를 두지 않는다 — 램프 구간에서 소수 VU 가 100 재고를 먼저 소진하면 피크 동시성이 '빈 재고'를
      // 두고 경쟁해 lost-update 창이 닫힌다. startVUs 를 PEAK 로 둬 t=0 부터 전 VU 가 재고가 가득한 순간을
      // 동시에 친다(이슈: 스파이크형 — ramping-vus 급상승). VU 는 init 단계에서 미리 생성되어 곧장 활성화된다.
      startVUs: PEAK_VUS,
      stages: [
        { duration: '3s', target: PEAK_VUS }, // t=0 부터 최대 동시성 유지
        { duration: '1s', target: 0 }, // 감쇠
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // Before 측정이므로 실패를 게이트하지 않는다 — 소진 후 409, 단일 행 경합으로 인한 5xx 는 기대되는 결과다.
    // 유일한 하드 게이트: 주문이 최소 1건은 실제로 생성돼야 한다(setup 실패·전량 거절로 0건 통과 방지).
    order_created: ['count>0'],
  },
  // k6 기본 요약은 p(99) 를 출력하지 않으므로 명시 — 결과물에 p95·p99 가 드러나도록.
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const email = seedEmail(EMAIL_PREFIX, 3, EMAIL_DOMAIN); // loadtest001@groove.test ...

// admin 으로 로그인해 토큰을 얻는다(재고 리셋 PATCH 용). 실패 시 즉시 중단.
function loginAdmin() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { phase: 'setup-login' } },
  );
  if (res.status !== 200 && res.status !== 201) {
    throw new Error(`admin 로그인 실패(${res.status}) — 시드/자격증명 확인(${ADMIN_EMAIL} / ${ADMIN_PASSWORD}). scripts/seed.sh`);
  }
  const token = res.json('accessToken');
  if (!token) {
    throw new Error('admin 로그인 응답에 accessToken 이 없다 — 인증 응답 스키마 확인');
  }
  return token;
}

// 타깃 앨범 재고를 INITIAL_STOCK 으로 정확히 리셋한다(재실행마다 동일 조건). PATCH /stock 은 delta 증감이므로
// 현재 재고를 읽어 차이를 보낸다. SELLING 이 아니면 모든 주문이 422 가 되어 측정이 무의미하므로 중단한다.
function resetTargetStock(adminToken) {
  const detail = http.get(`${BASE_URL}/api/v1/albums/${TARGET_ALBUM_ID}`, { tags: { phase: 'setup-reset' } });
  if (detail.status !== 200) {
    throw new Error(`타깃 앨범 조회 실패(${detail.status}) — TARGET_ALBUM_ID=${TARGET_ALBUM_ID} 가 존재하는지 확인`);
  }
  const status = detail.json('status');
  if (status !== 'SELLING') {
    throw new Error(`타깃 앨범(id=${TARGET_ALBUM_ID})이 SELLING 이 아니다(status=${status}) — 판매중 한정반을 고르라`);
  }
  if (detail.json('isLimited') !== true) {
    // 한정반 여부는 주문·재고 차감 경로에 영향이 없어(코스메틱) 중단하지 않지만, '한정반 오버셀' 시나리오
    // 정합성을 위해 한정반 타깃을 권한다.
    console.warn(
      `타깃 앨범(id=${TARGET_ALBUM_ID})이 한정반(is_limited)이 아니다 — 시나리오 정합성을 위해 한정반을 고르라: ` +
        `SELECT id FROM album WHERE is_limited=1 AND status='SELLING' ORDER BY id LIMIT 1;`,
    );
  }
  const current = detail.json('stock');
  const delta = INITIAL_STOCK - current;
  if (delta !== 0) {
    const patched = http.patch(
      `${BASE_URL}/api/v1/admin/albums/${TARGET_ALBUM_ID}/stock`,
      JSON.stringify({ delta }),
      { headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' }, tags: { phase: 'setup-reset' } },
    );
    if (patched.status !== 200 || patched.json('stock') !== INITIAL_STOCK) {
      throw new Error(`재고 리셋 실패(status=${patched.status}, stock=${patched.json('stock')}) — admin 권한/엔드포인트 확인`);
    }
  }
  console.log(`타깃 앨범 id=${TARGET_ALBUM_ID} 재고 리셋 완료: ${current} → ${INITIAL_STOCK} (한정반 '${detail.json('title')}')`);
}

// 1회: admin 으로 재고를 100 으로 리셋하고, 시드 회원 N명의 토큰 풀을 만든다.
export function setup() {
  const adminToken = loginAdmin();
  resetTargetStock(adminToken);
  const tokens = buildTokenPool({ baseUrl: BASE_URL, count: MEMBER_COUNT, password: PASSWORD, email });
  return { tokens, targetAlbumId: TARGET_ALBUM_ID, initialStock: INITIAL_STOCK, runTag: `${Date.now()}` };
}

export default function (data) {
  // 시나리오 전역 반복 카운터로 토큰을 라운드로빈 — 풀 크기까지 회원이 겹치지 않는다(같은 앨범에 동시 쇄도).
  const n = exec.scenario.iterationInTest;
  const token = data.tokens[n % data.tokens.length];
  const body = buildSingleAlbumOrder(data.targetAlbumId, 1); // 모든 VU 가 동일 한정반 1개를 주문 → 재고 경합

  const res = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify(body), {
    // 주문 생성은 Idempotency-Key 필수 — 매 반복 고유 키(run 태그 + 전역 반복번호)로 중복 없이 신규 주문
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': `fs-${data.runTag}-${n}` },
    tags: { phase: 'flash' },
    // 201 생성·409 재고부족·422 구매불가는 정상 도메인 응답 → http_req_failed 에서 제외. 단일 행 경합으로 인한
    // 5xx(락 타임아웃·데드락 등)는 일부러 expected 에 넣지 않는다 — Before 의 실패 양상을 http_req_failed 로 드러낸다.
    responseCallback: http.expectedStatuses(201, 409, 422),
  });

  if (res.status === 201) {
    orderCreated.add(1);
    orderLatency.add(res.timings.duration);
  } else if (res.status === 409) {
    orderSoldout.add(1);
  } else {
    orderFailed.add(1); // 422 구매불가 · 5xx 락경합 등
  }

  check(res, {
    '201 생성 또는 409 소진 (정상 도메인 응답)': (r) => r.status === 201 || r.status === 409,
    'orderNumber 존재(201 시)': (r) => r.status !== 201 || !!r.json('orderNumber'),
  });
}

// 종료 후 타깃 앨범의 최종 재고를 읽어 소진량을 로깅한다(handleSummary 의 created 수와 대조 → lost-update 판정).
export function teardown(data) {
  const detail = http.get(`${BASE_URL}/api/v1/albums/${data.targetAlbumId}`);
  if (detail.status !== 200) {
    console.warn(`최종 재고 조회 실패(${detail.status}) — 수동 확인: SELECT stock FROM album WHERE id=${data.targetAlbumId};`);
    return;
  }
  const finalStock = detail.json('stock');
  finalStockGauge.add(finalStock); // handleSummary 가 created 와 합산해 lost-update 를 자동 판정하도록 metric 으로 넘긴다
  const consumed = data.initialStock - finalStock;
  console.log(
    `[#194 오버셀 baseline] targetAlbumId=${data.targetAlbumId}, initialStock=${data.initialStock}, ` +
      `finalStock=${finalStock}, consumed=${consumed}`,
  );
}

export function handleSummary(data) {
  const created = data.metrics.order_created ? data.metrics.order_created.values.count : 0;
  const soldout = data.metrics.order_soldout ? data.metrics.order_soldout.values.count : 0;
  const failed = data.metrics.order_failed ? data.metrics.order_failed.values.count : 0;
  // teardown 이 emit 한 최종 재고 Gauge. 있으면 lost-update 까지 자동 판정한다(=== null 로 0 재고와 구분).
  const finalStock = data.metrics.final_stock ? data.metrics.final_stock.values.value : null;

  let verdict;
  if (finalStock === null) {
    // teardown 재고 조회 실패 — DoD 기준(created > 100)만으로 약식 판정.
    verdict =
      created > INITIAL_STOCK
        ? `오버셀 재현됨(created ${created} > 재고 ${INITIAL_STOCK}); 최종 재고 미확인`
        : `created ≤ ${INITIAL_STOCK} 이고 최종 재고 미확인 — 수동 확인 필요`;
  } else {
    const consumed = INITIAL_STOCK - finalStock; // 실제 재고 감소량
    const lost = created - consumed; // 영속화된 주문 − 실제 차감 = lost-update 수(이론상 ≥ 0)
    if (created > INITIAL_STOCK) {
      verdict = `오버셀 재현됨(created ${created} > 재고 ${INITIAL_STOCK}; lost-update ${lost}건, finalStock=${finalStock})`;
    } else if (lost > 0) {
      verdict = `오버셀(lost-update) 재현됨 — created ${created} > 실제 차감 ${consumed} (lost-update ${lost}건, finalStock=${finalStock})`;
    } else {
      verdict = `오버셀 없음 — created ${created} == 실제 차감 ${consumed} (finalStock=${finalStock})`;
    }
  }
  console.log(
    `[#194 오버셀 baseline] order_created(201)=${created}, order_soldout(409)=${soldout}, order_failed(기타)=${failed} → ${verdict}`,
  );
  // setup() 토큰 풀(실제 JWT) 비식별화 + 표준 산출물 — 공용 헬퍼로 통일(#219). verdict 는 metrics 만 읽어 무관.
  return redactedSummary(data, 'loadtest/flash-sale-summary.json');
}
