import client from './client'

// 배송 추적 API. GET /shippings/{trackingNumber} 는 공개(운송장 번호를 알면 누구나 조회).
// 운송장 번호는 주문 응답(OrderResponse.trackingNumber)에서 얻는다.

/** 배송 추적 조회 → ShippingResponse{trackingNumber, status, recipientName, address, shippedAt, deliveredAt, ...}. */
export function trackShipping(trackingNumber) {
  return client.get(`/shippings/${trackingNumber}`, { auth: false }).then((res) => res.data)
}
