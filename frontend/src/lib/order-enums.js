// 구매 여정(주문·결제·배송·쿠폰) enum ↔ 한글 라벨/헬퍼 — 백엔드 enum 과 1:1. (#116)
// lib/enums.js(카탈로그)와 동일하게 단일 출처로 둔다.

import { formatWon } from '@/lib/format'

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

/** PaymentMethod 선택 옵션(결제 수단 드롭다운). */
export const PAYMENT_METHOD_OPTIONS = [
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

/** 리뷰 작성 가능 상태 — 클라 노출 가드(백엔드 REVIEWABLE_ORDER_STATUSES 미러, 최종 판정은 서버). */
export function isReviewableStatus(status) {
  return status === 'DELIVERED' || status === 'COMPLETED'
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

/** MemberCouponStatus enum → 한글 라벨. */
export const COUPON_STATUS_LABEL = {
  ISSUED: '사용 가능',
  USED: '사용 완료',
  EXPIRED: '기간 만료',
  CANCELLED: '취소됨',
}

/** 내 쿠폰 상태 필터 옵션(내 쿠폰 목록) — 전체 + 4개 상태. */
export const COUPON_STATUS_FILTER_OPTIONS = [
  { value: '', label: '전체' },
  { value: 'ISSUED', label: '사용 가능' },
  { value: 'USED', label: '사용 완료' },
  { value: 'EXPIRED', label: '기간 만료' },
  { value: 'CANCELLED', label: '취소됨' },
]

export function couponStatusLabel(status) {
  return COUPON_STATUS_LABEL[status] || status || ''
}

/**
 * 쿠폰 할인 표시 라벨 — 쿠폰 목록 카드·내 쿠폰 공유. CouponResponse/MemberCouponResponse 공통 필드로 동작.
 * 예) "5,000원 할인", "10% 할인 (최대 3,000원)", "10% 할인 · 20,000원 이상".
 * @param {{discountType:string, discountValue:number, maxDiscountAmount:number|null, minOrderAmount:number}} coupon
 */
export function couponDiscountLabel(coupon) {
  if (!coupon) return ''
  let value
  if (coupon.discountType === 'PERCENTAGE') {
    value = `${coupon.discountValue}% 할인`
    if (coupon.maxDiscountAmount != null) value += ` (최대 ${formatWon(coupon.maxDiscountAmount)})`
  } else {
    value = `${formatWon(coupon.discountValue)} 할인`
  }
  const min = coupon.minOrderAmount > 0 ? ` · ${formatWon(coupon.minOrderAmount)} 이상` : ''
  return value + min
}

/**
 * 쿠폰 발급 ApiError → 분류 키 — 쿠폰 목록(CouponListView) 발급의 단일 출처.
 * 429(wire code SYSTEM_002)=rate-limit, 409 소진/중복, 422 발급불가, 그 외 오류.
 * @returns {'SOLD_OUT'|'ALREADY'|'RATE_LIMIT'|'NOT_ISSUABLE'|'ERROR'}
 */
export function classifyCouponIssueError(e) {
  if (e?.status === 429 || e?.code === 'SYSTEM_002') return 'RATE_LIMIT'
  if (e?.code === 'COUPON_SOLD_OUT') return 'SOLD_OUT'
  if (e?.code === 'COUPON_ALREADY_ISSUED' || e?.code === 'IDEMPOTENCY_IN_PROGRESS') return 'ALREADY'
  if (e?.code === 'COUPON_NOT_ISSUABLE') return 'NOT_ISSUABLE'
  return 'ERROR'
}

/** 쿠폰 발급 실패 분류 → 사용자 안내 메시지(토스트용). ERROR 는 호출부에서 errorMessage 로 폴백. */
export const COUPON_ISSUE_ERROR_MESSAGE = {
  SOLD_OUT: '이미 소진된 쿠폰입니다.',
  ALREADY: '이미 발급받은 쿠폰입니다.',
  NOT_ISSUABLE: '지금은 발급할 수 없는 쿠폰입니다.',
  RATE_LIMIT: '요청이 많습니다. 잠시 후 다시 시도해 주세요.',
}
