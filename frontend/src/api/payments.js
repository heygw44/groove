import client from './client'

// 결제 API. POST /payments 는 permitAll(회원/게스트). GET /payments/{id} 는 회원 전용.

/** 결제 요청(202 Accepted, status PENDING). idempotencyKey 를 명시 헤더로 전달. → PaymentApiResponse. */
export function requestPayment(orderNumber, method, idempotencyKey) {
  return client
    .post(
      '/payments',
      { orderNumber, method },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    )
    .then((res) => res.data)
}

/** 결제 상태 조회(회원). 폴링 대상(PENDING → PAID/FAILED). → PaymentApiResponse. */
export function getPayment(paymentId) {
  return client.get(`/payments/${paymentId}`).then((res) => res.data)
}

/**
 * 토스 결제 요청(checkout). PENDING 결제를 서버 저장하고 결제위젯 초기화 값을 받는다.
 * idempotencyKey 를 명시 헤더로 전달. → { clientKey, orderId, amount, successUrl, failUrl }.
 * clientKey·successUrl·failUrl 은 백엔드 dev/prod(토스 설정) 에서만 채워지고, 그 외엔 null.
 */
export function tossCheckout(orderNumber, method, idempotencyKey) {
  return client
    .post(
      '/payments/toss/checkout',
      { orderNumber, method },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    )
    .then((res) => res.data)
}
