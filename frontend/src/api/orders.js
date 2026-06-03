import client from './client'

// 주문 API. 생성은 회원/게스트 공용(POST /orders permitAll) — 로그인 상태면 client 가 Bearer 를 자동
// 첨부해 회원 주문, 아니면 토큰 없이 게스트 주문이 된다(body 의 guest 블록 유무로 구성한다).

/**
 * 주문 생성. → OrderResponse.
 * @param {object} body {items:[{albumId,quantity}], guest?:{email,phone}, shipping:{...}, memberCouponId?}
 */
export function createOrder(body) {
  return client.post('/orders', body).then((res) => res.data)
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

/**
 * 내 주문 목록(회원). → PageResponse<OrderSummary>.
 * @param {{status?:string, page?:number, size?:number}} [params] sort 는 백엔드 고정(createdAt,desc).
 */
export function myOrders(params = {}) {
  return client.get('/members/me/orders', { params }).then((res) => res.data)
}
