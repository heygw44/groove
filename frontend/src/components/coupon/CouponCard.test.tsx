import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { CouponCard } from '@/components/coupon/CouponCard';
import type { MemberCoupon } from '@/types/coupon';

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
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-04T12:00:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
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
