import type { CouponDiscount, MemberCoupon, MemberCouponStatus } from '@/types/coupon';
import { formatPrice } from '@/utils/formatPrice';
import { getServerNow } from '@/utils/serverTime';

const MEMBER_COUPON_STATUSES: readonly MemberCouponStatus[] = ['usable', 'used', 'expired'];
const DEFAULT_STATUS: MemberCouponStatus = 'usable';

export function formatCouponDiscount(coupon: CouponDiscount): string {
  if (coupon.discountType === 'FIXED') {
    return formatPrice(coupon.discountValue);
  }

  const rate = `${coupon.discountValue}%`;
  return coupon.maxDiscountAmount !== undefined
    ? `${rate} (최대 ${formatPrice(coupon.maxDiscountAmount)})`
    : rate;
}

/** used·expired 는 서버가 각각 내려주는 독립된 플래그라 우선순위를 정해 하나로 합친다. */
export function getMemberCouponStatus(coupon: MemberCoupon): MemberCouponStatus {
  if (coupon.used) {
    return 'used';
  }
  if (coupon.expired) {
    return 'expired';
  }
  return 'usable';
}

const startOfDay = (date: Date): Date =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate());

const MS_PER_DAY = 1000 * 60 * 60 * 24;

/** 시각이 아니라 날짜 기준 D-day 이므로 양쪽을 로컬 자정으로 내려서 뺀다. */
export function getDaysUntil(dateIso: string, now: Date = getServerNow()): number {
  const target = startOfDay(new Date(dateIso));
  const today = startOfDay(now);
  return Math.round((target.getTime() - today.getTime()) / MS_PER_DAY);
}

export function formatDDay(days: number): string {
  if (days < 0) {
    return '만료';
  }
  return days === 0 ? 'D-Day' : `D-${days}`;
}

export function parseMemberCouponStatus(searchParams: URLSearchParams): MemberCouponStatus {
  const value = searchParams.get('status');
  return MEMBER_COUPON_STATUSES.includes(value as MemberCouponStatus)
    ? (value as MemberCouponStatus)
    : DEFAULT_STATUS;
}

/** 기본값(usable)은 URL 을 지저분하게 만들 뿐이라 생략한다. */
export function serializeMemberCouponStatus(status: MemberCouponStatus): URLSearchParams {
  const params = new URLSearchParams();
  if (status !== DEFAULT_STATUS) {
    params.set('status', status);
  }
  return params;
}
