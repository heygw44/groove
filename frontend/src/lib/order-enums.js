// 구매 여정(주문·결제·배송·쿠폰) enum ↔ 한글 라벨/헬퍼 — 백엔드 enum 과 1:1. (#116)
// lib/enums.js(카탈로그)와 동일하게 단일 출처로 둔다.

/** OrderStatus enum → 한글 라벨. */
export const ORDER_STATUS_LABEL = {
  PENDING: '결제 대기',
  PAID: '결제 완료',
  PREPARING: '배송 준비',
  SHIPPED: '배송 중',
  DELIVERED: '배송 완료',
  COMPLETED: '구매 확정',
  CANCELLED: '취소됨',
  PAYMENT_FAILED: '결제 실패',
}

/** PaymentStatus enum → 한글 라벨. */
export const PAYMENT_STATUS_LABEL = {
  PENDING: '결제 확인 중',
  PAID: '결제 완료',
  FAILED: '결제 실패',
  REFUNDED: '환불됨',
}

/** ShippingStatus enum → 한글 라벨. */
export const SHIPPING_STATUS_LABEL = {
  PREPARING: '배송 준비 중',
  SHIPPED: '배송 중',
  DELIVERED: '배송 완료',
}

/** 배송 진행 단계(표시 순서) — 진행 바 렌더용. */
export const SHIPPING_STEPS = ['PREPARING', 'SHIPPED', 'DELIVERED']

/** PaymentMethod 선택 옵션. 로컬 mock PG 는 수단과 무관하게 자동 전이하므로 데모 기본값은 MOCK. */
export const PAYMENT_METHOD_OPTIONS = [
  { value: 'MOCK', label: '테스트 결제 (데모)' },
  { value: 'CARD', label: '신용/체크카드' },
  { value: 'BANK_TRANSFER', label: '계좌이체' },
]

/** OrderStatus 필터 옵션(주문 목록) — 전체 + 주요 상태. */
export const ORDER_STATUS_FILTER_OPTIONS = [
  { value: '', label: '전체' },
  { value: 'PENDING', label: '결제 대기' },
  { value: 'PAID', label: '결제 완료' },
  { value: 'DELIVERED', label: '배송 완료' },
  { value: 'CANCELLED', label: '취소됨' },
  { value: 'PAYMENT_FAILED', label: '결제 실패' },
]

export function orderStatusLabel(status) {
  return ORDER_STATUS_LABEL[status] || status || ''
}

export function paymentStatusLabel(status) {
  return PAYMENT_STATUS_LABEL[status] || status || ''
}

export function shippingStatusLabel(status) {
  return SHIPPING_STATUS_LABEL[status] || status || ''
}

/** 취소 가능 상태 — 클라 버튼 노출 가드(최종 판정은 서버, 불가 시 409). */
export function isCancellableStatus(status) {
  return status === 'PENDING' || status === 'PAID'
}

/** 결제 완료(배송이 생성·진행되는) 상태군. */
export function isPaidStatus(status) {
  return ['PAID', 'PREPARING', 'SHIPPED', 'DELIVERED', 'COMPLETED'].includes(status)
}

/**
 * 쿠폰 할인액 미리보기(표시용) — 최종 금액은 서버 OrderResponse.payableAmount 가 권위.
 * @param {{discountType:string, discountValue:number, maxDiscountAmount:number|null, minOrderAmount:number}} coupon
 * @param {number} orderAmount 상품 합계(할인 전)
 * @returns {number} 0 이상의 할인액(주문액 초과 안 함)
 */
export function previewDiscount(coupon, orderAmount) {
  if (!coupon || orderAmount < (coupon.minOrderAmount ?? 0)) return 0
  let discount
  if (coupon.discountType === 'PERCENTAGE') {
    discount = Math.floor((orderAmount * coupon.discountValue) / 100)
    if (coupon.maxDiscountAmount != null) discount = Math.min(discount, coupon.maxDiscountAmount)
  } else {
    discount = coupon.discountValue
  }
  return Math.max(0, Math.min(discount, orderAmount))
}
