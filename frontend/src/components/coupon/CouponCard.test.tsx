import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';

import { CouponCard } from '@/components/coupon/CouponCard';
import type { MemberCoupon } from '@/types/coupon';
import { applyServerTime } from '@/utils/serverTime';

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

describe('CouponCard', () => {
  // D-day 는 getServerNow() 기준이라 시스템 시계가 아니라 서버 시각 앵커를 고정해야 실제 날짜와 무관해진다.
  beforeEach(() => {
    applyServerTime('2026-09-04T12:00:00+09:00');
  });

  it('사용 완료 쿠폰은 사용 완료 배지를 보여준다', () => {
    // given & when
    render(<CouponCard coupon={memberCoupon({ used: true, usedAt: '2026-09-02T00:00:00' })} />);

    // then
    expect(screen.getByText('사용 완료')).toBeInTheDocument();
  });

  it('사용 가능한 쿠폰은 D-day 텍스트를 보여준다', () => {
    // given & when
    render(<CouponCard coupon={memberCoupon({ expiresAt: '2026-09-06T00:00:00' })} />);

    // then
    expect(screen.getByText('D-2')).toBeInTheDocument();
  });
});
