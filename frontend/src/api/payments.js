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
