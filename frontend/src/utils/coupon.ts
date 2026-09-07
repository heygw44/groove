import type { CouponDiscount, MemberCoupon, MemberCouponStatus } from '@/types/coupon';
import { formatPrice } from '@/utils/formatPrice';
import { getServerNow, toServerMs } from '@/utils/serverTime';

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

const MS_PER_DAY = 1000 * 60 * 60 * 24;
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

/**
 * epoch 를 KST 자정 기준 날짜로 내린다. 쿠폰 만료일은 KST 달력 기준 개념이라, 호스트(뷰어)
 * 타임존의 getFullYear() 를 쓰면 자정 부근 값이 호스트 타임존에 따라 다른 날짜로 갈린다.
 * 그래서 UTC 컴포넌트를 그대로 써서(KST 로 밀어둔 뒤 UTC 라벨을 읽는 트릭) 어느 호스트에서
 * 돌려도 같은 KST 날짜가 나오게 한다.
 */
const startOfDayKst = (epochMs: number): number => {
  const kst = new Date(epochMs + KST_OFFSET_MS);
  return Date.UTC(kst.getUTCFullYear(), kst.getUTCMonth(), kst.getUTCDate());
};

/**
 * 시각이 아니라 날짜 기준 D-day 이므로 양쪽을 KST 자정으로 내려서 뺀다.
 * dateIso 는 오프셋 없는 서버 LocalDateTime 이라 toServerMs 로 KST 해석을 거쳐야
 * now(서버 시각)와 기준이 맞는다 - 그냥 new Date() 로 파싱하면 KST 밖에서 하루 어긋난다.
 */
export function getDaysUntil(dateIso: string, now: Date = getServerNow()): number {
  const target = startOfDayKst(toServerMs(dateIso));
  const today = startOfDayKst(now.getTime());
  return Math.round((target - today) / MS_PER_DAY);
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
