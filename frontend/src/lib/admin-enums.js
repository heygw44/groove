// 관리자 콘솔(#119) 전용 enum ↔ 라벨/옵션/전이 헬퍼. 백엔드 enum·상태머신과 1:1.
// 공개 카탈로그용 매핑은 lib/enums.js, 구매여정용은 lib/order-enums.js 에 있고 여기서 재사용한다.

import { errorMessage } from '@/lib/problem-detail'
import { FORMAT_LABEL, STATUS_LABEL } from '@/lib/enums'
import { ORDER_STATUS_LABEL } from '@/lib/order-enums'

// ── 앨범 ───────────────────────────────────────────────────────────────────

/** 앨범 폼 status 옵션(AlbumStatus 전체 — Public 과 달리 HIDDEN 포함). STATUS_LABEL 파생. */
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

/**
 * 관리자 강제 전환 가능한 "다음 상태" 단일 경로 — 백엔드 OrderStatus 전이 규칙 ∩ FORCEABLE_TARGETS.
 * (PAID→PREPARING→SHIPPED→DELIVERED→COMPLETED). CANCELLED 는 강제 대상이 아니다(취소=환불 경로).
 * 매핑에 없는 상태(PENDING·종착 상태)는 수동 전환 불가 → 버튼 미노출.
 */
export const ORDER_NEXT_FORCEABLE_STATUS = {
  PAID: 'PREPARING',
  PREPARING: 'SHIPPED',
  SHIPPED: 'DELIVERED',
  DELIVERED: 'COMPLETED',
}

/** 현재 상태에서 강제 전환 가능한 다음 상태(없으면 null). 최종 판정은 서버(불가 시 409). */
export function nextForceableStatus(status) {
  return ORDER_NEXT_FORCEABLE_STATUS[status] ?? null
}

/**
 * 환불(취소) 버튼 노출 가드 — 백엔드 refund 는 결제 PAID + order.status→CANCELLED 합법 전이를 요구한다
 * (= PAID·PREPARING). SHIPPED 이후/취소/실패/완료 주문엔 버튼을 숨긴다(최종 판정은 서버 409/422).
 */
export function isRefundableStatus(status) {
  return status === 'PAID' || status === 'PREPARING'
}

// ── 쿠폰 ───────────────────────────────────────────────────────────────────

/** CouponStatus(쿠폰 정책 상태) → 한글 라벨. MemberCouponStatus(order-enums)와 다른 enum. */
export const COUPON_ADMIN_STATUS_LABEL = {
  ACTIVE: '활성',
  SUSPENDED: '중단',
  ENDED: '종료',
}

export function couponAdminStatusLabel(status) {
  return COUPON_ADMIN_STATUS_LABEL[status] || status || ''
}

/** 관리자 쿠폰 목록 status 필터 — 라벨은 COUPON_ADMIN_STATUS_LABEL 에서 파생(단일 출처). */
export const ADMIN_COUPON_STATUS_FILTER_OPTIONS = [
  { value: '', label: '전체' },
  ...Object.entries(COUPON_ADMIN_STATUS_LABEL).map(([value, label]) => ({ value, label })),
]

/** 쿠폰 생성 폼 할인 타입 옵션(CouponDiscountType). */
export const COUPON_DISCOUNT_TYPE_OPTIONS = [
  { value: 'FIXED_AMOUNT', label: '정액 할인 (원)' },
  { value: 'PERCENTAGE', label: '정률 할인 (%)' },
]

/**
 * 쿠폰 상태 전이 규칙 — 현재 상태에서 변경 가능한 대상 목록(백엔드 미러, 최종 판정은 서버 409).
 * ACTIVE → SUSPENDED/ENDED, SUSPENDED → ACTIVE/ENDED, ENDED → (종착).
 */
export function couponStatusTransitions(current) {
  if (current === 'ACTIVE') return ['SUSPENDED', 'ENDED']
  if (current === 'SUSPENDED') return ['ACTIVE', 'ENDED']
  return []
}

// ── 에러 ───────────────────────────────────────────────────────────────────

/**
 * 관리자 작업 ApiError → 사용자 안내 메시지(토스트용). 권한/전이불가/미존재를 분류하고
 * 그 외는 서버 detail 또는 fallback 으로 폴백한다. (api/coupons.js classifyCouponIssueError 패턴 차용)
 */
export function adminErrorMessage(e, fallback) {
  if (e?.status === 403) return '권한이 없습니다. 관리자 계정으로 다시 시도해 주세요.'
  if (e?.status === 409) return e?.detail || '현재 상태에서는 허용되지 않는 작업입니다.'
  if (e?.status === 404) return e?.detail || '대상을 찾을 수 없습니다.'
  return errorMessage(e, fallback)
}
