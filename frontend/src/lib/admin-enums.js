// 관리자 콘솔 전용 enum ↔ 라벨/옵션/전이 헬퍼.

import { errorMessage } from '@/lib/problem-detail'
import { FORMAT_LABEL, STATUS_LABEL } from '@/lib/enums'
import { ORDER_STATUS_LABEL } from '@/lib/order-enums'

// ── 앨범 ───────────────────────────────────────────────────────────────────

/** 앨범 폼 status 옵션(AlbumStatus 전체, HIDDEN 포함). STATUS_LABEL 파생. */
export const ALBUM_STATUS_OPTIONS = Object.entries(STATUS_LABEL).map(([value, label]) => ({
  value,
  label,
}))

/** 앨범 폼 format 옵션(AlbumFormat 전체). FORMAT_LABEL 파생. */
export const ALBUM_FORMAT_OPTIONS = Object.entries(FORMAT_LABEL).map(([value, label]) => ({
  value,
  label,
}))

/** 관리자 앨범 목록 status 필터 — 전체 + 3개 상태. */
export const ADMIN_ALBUM_STATUS_FILTER_OPTIONS = [
  { value: '', label: '전체' },
  ...ALBUM_STATUS_OPTIONS,
]

// ── 주문 ───────────────────────────────────────────────────────────────────

/** 관리자 주문 목록 status 필터 — 전체 + OrderStatus 8종. */
export const ADMIN_ORDER_STATUS_FILTER_OPTIONS = [
  { value: '', label: '전체' },
  ...Object.entries(ORDER_STATUS_LABEL).map(([value, label]) => ({ value, label })),
]

/** 관리자 강제 전환 가능한 "다음 상태" 경로(PAID→PREPARING→SHIPPED→DELIVERED→COMPLETED). */
export const ORDER_NEXT_FORCEABLE_STATUS = {
  PAID: 'PREPARING',
  PREPARING: 'SHIPPED',
  SHIPPED: 'DELIVERED',
  DELIVERED: 'COMPLETED',
}

/** 현재 상태에서 강제 전환 가능한 다음 상태(없으면 null). */
export function nextForceableStatus(status) {
  return ORDER_NEXT_FORCEABLE_STATUS[status] ?? null
}

/** 환불(취소) 버튼 노출 가드 — PAID·PREPARING 만 true. */
export function isRefundableStatus(status) {
  return status === 'PAID' || status === 'PREPARING'
}

// ── 쿠폰 ───────────────────────────────────────────────────────────────────

/** CouponStatus(쿠폰 정책 상태) → 한글 라벨. */
export const COUPON_ADMIN_STATUS_LABEL = {
  ACTIVE: '활성',
  SUSPENDED: '중단',
  ENDED: '종료',
}

export function couponAdminStatusLabel(status) {
  return COUPON_ADMIN_STATUS_LABEL[status] || status || ''
}

/** 관리자 쿠폰 목록 status 필터 — COUPON_ADMIN_STATUS_LABEL 에서 파생. */
export const ADMIN_COUPON_STATUS_FILTER_OPTIONS = [
  { value: '', label: '전체' },
  ...Object.entries(COUPON_ADMIN_STATUS_LABEL).map(([value, label]) => ({ value, label })),
]

/** 쿠폰 생성 폼 할인 타입 옵션(CouponDiscountType). */
export const COUPON_DISCOUNT_TYPE_OPTIONS = [
  { value: 'FIXED_AMOUNT', label: '정액 할인 (원)' },
  { value: 'PERCENTAGE', label: '정률 할인 (%)' },
]

/** 쿠폰 상태 전이 규칙 — 현재 상태에서 변경 가능한 대상 목록(ACTIVE↔SUSPENDED, →ENDED, ENDED 종착). */
export function couponStatusTransitions(current) {
  if (current === 'ACTIVE') return ['SUSPENDED', 'ENDED']
  if (current === 'SUSPENDED') return ['ACTIVE', 'ENDED']
  return []
}

// ── 에러 ───────────────────────────────────────────────────────────────────

/** 관리자 작업 ApiError → 사용자 안내 메시지(토스트용). 403/409/404 분류, 그 외는 폴백. */
export function adminErrorMessage(e, fallback) {
  if (e?.status === 403) return '권한이 없습니다. 관리자 계정으로 다시 시도해 주세요.'
  if (e?.status === 409) return e?.detail || '현재 상태에서는 허용되지 않는 작업입니다.'
  if (e?.status === 404) return e?.detail || '대상을 찾을 수 없습니다.'
  return errorMessage(e, fallback)
}
