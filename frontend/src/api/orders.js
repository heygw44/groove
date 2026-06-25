import client from './client'

// 주문 API. 생성은 회원/게스트 공용(POST /orders permitAll) — 로그인 상태면 client 가 Bearer 를 자동 첨부.

/**
 * 주문 생성. 호출자가 안정적인 멱등 키를 넘긴다 — 사용자 재제출(더블클릭·새로고침)에도 같은 키를 보내야
 * 서버가 중복 주문을 막는다(매 호출 새 UUID 면 멱등 보호가 작동하지 않음). → OrderResponse.
 */
export function createOrder(body, idempotencyKey) {
  return client
    .post('/orders', body, { headers: { 'Idempotency-Key': idempotencyKey } })
    .then((res) => res.data)
}

/** 주문 단건 조회(회원 본인). → OrderResponse. */
export function getOrder(orderNumber) {
  return client.get(`/orders/${orderNumber}`).then((res) => res.data)
}

/** 주문 취소(회원 본인). reason 선택. → OrderResponse. */
export function cancelOrder(orderNumber, reason) {
  return client.post(`/orders/${orderNumber}/cancel`, { reason }).then((res) => res.data)
}

/** 게스트 주문 조회 — orderNumber + email 페어 매칭(인증 불필요). → OrderResponse. */
export function guestLookup(orderNumber, email) {
  return client
    .post(`/orders/${orderNumber}/guest-lookup`, { email }, { auth: false })
    .then((res) => res.data)
}

/** 내 주문 목록(회원). → PageResponse<OrderSummary>. */
export function myOrders(params = {}) {
  return client.get('/members/me/orders', { params }).then((res) => res.data)
}
