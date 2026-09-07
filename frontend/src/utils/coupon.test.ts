import { describe, expect, it } from 'vitest';

import type { MemberCoupon } from '@/types/coupon';
import {
  formatCouponDiscount,
  formatDDay,
  getDaysUntil,
  getMemberCouponStatus,
  parseMemberCouponStatus,
  serializeMemberCouponStatus,
} from '@/utils/coupon';
import { toServerMs } from '@/utils/serverTime';

const memberCoupon = (overrides: Partial<MemberCoupon> = {}): MemberCoupon => ({
  memberCouponId: 1,
  couponId: 1,
  couponCode: 'WELCOME10',
  couponName: '신규가입 쿠폰',
  discountType: 'FIXED',
  discountValue: 5000,
  minOrderAmount: 30000,
  expiresAt: '2026-09-10T00:00:00',
  used: false,
  expired: false,
  issuedAt: '2026-09-01T00:00:00',
  ...overrides,
});

describe('formatCouponDiscount()', () => {
  it('정액 할인은 원화로 표기한다', () => {
    // given
    const coupon = { discountType: 'FIXED' as const, discountValue: 5000, minOrderAmount: 0 };

    // when & then
    expect(formatCouponDiscount(coupon)).toBe('5,000원');
  });

  it('정률 할인은 퍼센트로 표기한다', () => {
    // given
    const coupon = { discountType: 'RATE' as const, discountValue: 10, minOrderAmount: 0 };

    // when & then
    expect(formatCouponDiscount(coupon)).toBe('10%');
  });

  it('정률 할인에 최대 할인액이 있으면 함께 표기한다', () => {
    // given
    const coupon = {
      discountType: 'RATE' as const,
      discountValue: 10,
      minOrderAmount: 0,
      maxDiscountAmount: 5000,
    };

    // when & then
    expect(formatCouponDiscount(coupon)).toBe('10% (최대 5,000원)');
  });
});

describe('getMemberCouponStatus()', () => {
  it('used 가 true 면 expired 여부와 무관하게 used 다', () => {
    // given
    const coupon = memberCoupon({ used: true, expired: true });

    // when & then
    expect(getMemberCouponStatus(coupon)).toBe('used');
  });

  it('used 가 false 이고 expired 가 true 면 expired 다', () => {
    // given
    const coupon = memberCoupon({ used: false, expired: true });

    // when & then
    expect(getMemberCouponStatus(coupon)).toBe('expired');
  });

  it('둘 다 false 면 usable 이다', () => {
    // given
    const coupon = memberCoupon({ used: false, expired: false });

    // when & then
    expect(getMemberCouponStatus(coupon)).toBe('usable');
  });
});

describe('getDaysUntil() / formatDDay()', () => {
  // now 는 서버 시각(KST) 을 흉내낸 값이라, dateIso 와 같은 방식(toServerMs)으로
  // KST 해석을 거쳐야 한다 - 그냥 new Date() 로 만들면 TZ=UTC 에서 자정 부근 케이스가 깨진다.
  const now = new Date(toServerMs('2026-09-04T15:00:00'));

  it('오늘 자정 기준으로 오늘이면 0 이고 D-Day 로 표기한다', () => {
    // given
    const days = getDaysUntil('2026-09-04T00:00:00', now);

    // when & then
    expect(days).toBe(0);
    expect(formatDDay(days)).toBe('D-Day');
  });

  it('내일이면 1 이고 D-1 로 표기한다', () => {
    // given
    const days = getDaysUntil('2026-09-05T09:00:00', now);

    // when & then
    expect(days).toBe(1);
    expect(formatDDay(days)).toBe('D-1');
  });

  it('지난 날짜면 음수이고 만료로 표기한다', () => {
    // given
    const days = getDaysUntil('2026-09-03T23:59:59', now);

    // when & then
    expect(days).toBe(-1);
    expect(formatDDay(days)).toBe('만료');
  });

  it('KST 자정 직후 날짜도 호스트 타임존과 무관하게 내일로 계산한다', () => {
    // given: 00:30 을 오프셋 없이 그대로 new Date() 로 파싱하면(예전 버그) 호스트가
    // UTC 일 때 "9/4 15:30" 이 아니라 "9/5 00:30" 을 그대로 UTC 로 읽어버려 여전히
    // 9/5 로는 나오지만, now 쪽 기준(오늘 = 9/4)과 섞이면서 결과가 흔들렸다.
    // toServerMs 로 양쪽 다 KST 해석을 거치면 호스트 TZ 와 무관하게 항상 1이어야 한다.
    const days = getDaysUntil('2026-09-05T00:30:00', now);

    // when & then
    expect(days).toBe(1);
    expect(formatDDay(days)).toBe('D-1');
  });
});

describe('parseMemberCouponStatus() / serializeMemberCouponStatus()', () => {
  it('status 파라미터가 없으면 usable 을 기본값으로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams();

    // when & then
    expect(parseMemberCouponStatus(searchParams)).toBe('usable');
  });

  it('알 수 없는 status 값도 usable 로 되돌린다', () => {
    // given
    const searchParams = new URLSearchParams('status=unknown');

    // when & then
    expect(parseMemberCouponStatus(searchParams)).toBe('usable');
  });

  it('유효한 status 값은 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams('status=used');

    // when & then
    expect(parseMemberCouponStatus(searchParams)).toBe('used');
  });

  it('기본값(usable)은 빈 쿼리스트링으로 직렬화한다', () => {
    // given & when
    const params = serializeMemberCouponStatus('usable');

    // then
    expect(params.toString()).toBe('');
  });

  it('기본값이 아닌 상태는 쿼리스트링에 남긴다', () => {
    // given & when
    const params = serializeMemberCouponStatus('expired');

    // then
    expect(params.toString()).toBe('status=expired');
  });
});
