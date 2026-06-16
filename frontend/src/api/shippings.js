import client from './client'

// 배송 추적 API. GET /shippings/{trackingNumber} 는 공개.

/** 배송 추적 조회 → ShippingResponse{trackingNumber, status, recipientName, address, shippedAt, deliveredAt, ...}. */
export function trackShipping(trackingNumber) {
  return client.get(`/shippings/${trackingNumber}`, { auth: false }).then((res) => res.data)
}
