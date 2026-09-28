import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { CouponSection } from '@/components/order/CouponSection';
import { useAvailableCoupons } from '@/hooks/queries/useAvailableCoupons';
import type { AvailableCoupon } from '@/types/coupon';

vi.mock('@/hooks/queries/useAvailableCoupons', () => ({
  useAvailableCoupons: vi.fn(),
}));

const showToast = vi.fn();
vi.mock('@/components/common/toastContext', () => ({
  useToast: () => ({ showToast }),
}));

const buildCoupon = (overrides: Partial<AvailableCoupon> = {}): AvailableCoupon => ({
  memberCouponId: 1,
  couponCode: 'WELCOME',
  couponName: '웰컴 쿠폰',
  discountType: 'FIXED',
  discountValue: 3000,
  minOrderAmount: 10000,
  expiresAt: '2026-12-31T23:59:59',
  expectedDiscount: 3000,
  ...overrides,
});

const mockCoupons = (coupons: AvailableCoupon[] | undefined) => {
  vi.mocked(useAvailableCoupons).mockReturnValue({
    data: coupons,
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useAvailableCoupons>);
};

describe('CouponSection', () => {
  it('restoreCouponId 와 일치하는 쿠폰이 목록에 있으면 자동으로 선택한다', () => {
    // given
    const coupon = buildCoupon();
    mockCoupons([coupon, buildCoupon({ memberCouponId: 2, couponName: '다른 쿠폰' })]);
    const onSelect = vi.fn();

    // when
    render(
      <CouponSection orderAmount={20000} selected={null} onSelect={onSelect} restoreCouponId={1} />,
    );

    // then
    expect(onSelect).toHaveBeenCalledWith(coupon);
  });

  it('restoreCouponId 와 일치하는 쿠폰이 없으면 아무것도 선택하지 않는다', () => {
    // given
    mockCoupons([buildCoupon({ memberCouponId: 2 })]);
    const onSelect = vi.fn();

    // when
    render(
      <CouponSection
        orderAmount={20000}
        selected={null}
        onSelect={onSelect}
        restoreCouponId={999}
      />,
    );

    // then
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('restoreCouponId 가 없으면 자동 선택을 시도하지 않는다', () => {
    // given
    mockCoupons([buildCoupon()]);
    const onSelect = vi.fn();

    // when
    render(<CouponSection orderAmount={20000} selected={null} onSelect={onSelect} />);

    // then
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('선택된 쿠폰을 보여주고 할인 금액을 표시한다', () => {
    // given
    mockCoupons([buildCoupon()]);

    // when
    render(<CouponSection orderAmount={20000} selected={buildCoupon()} onSelect={vi.fn()} />);

    // then
    expect(screen.getByText('웰컴 쿠폰')).toBeInTheDocument();
    expect(screen.getByText('-3,000원')).toBeInTheDocument();
  });
});
