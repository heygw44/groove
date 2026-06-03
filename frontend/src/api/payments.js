import client from './client'

// 결제 API. POST /payments 는 permitAll(회원/게스트) — 로그인 상태면 client 가 Bearer 를 자동 첨부해
// 주문 소유자로 결제한다. GET /payments/{id} 는 회원 전용(게스트는 폴링 불가, 주문조회로 확인).

/**
 * 결제 요청(202 Accepted, status PENDING). → PaymentApiResponse.
 *
 * idempotencyKey 는 호출부(PaymentView)가 결제 시도당 1회 생성·보관해 넘긴다 — 더블클릭/재시도에도
 * 같은 키를 명시 헤더로 전달하면 서버가 동일 키 replay 로 중복 결제를 막는다. client 인터셉터는
 * 헤더에 이미 키가 있으면 덮어쓰지 않으므로(idempotent 옵션 미사용) 이 명시 키가 그대로 보존된다.
 *
 * @param {string} orderNumber
 * @param {string} method CARD | BANK_TRANSFER | MOCK
 * @param {string} idempotencyKey UUID v4 (lib/uuid randomUuid)
 */
export function requestPayment(orderNumber, method, idempotencyKey) {
  return client
    .post(
      '/payments',
      { orderNumber, method },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    )
    .then((res) => res.data)
}

/** 결제 상태 조회(회원). 폴링 대상 — PENDING → PAID/FAILED. → PaymentApiResponse. */
export function getPayment(paymentId) {
  return client.get(`/payments/${paymentId}`).then((res) => res.data)
}
