import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { OrderPriceSummary } from '@/components/order/OrderPriceSummary';

describe('OrderPriceSummary', () => {
  it('상품 금액·할인 금액·총 결제 금액을 보여준다', () => {
    // given & when
    render(<OrderPriceSummary totalAmount={30000} discountAmount={3000} finalAmount={27000} />);

    // then
    expect(screen.getByText('30,000원')).toBeInTheDocument();
    expect(screen.getByText('-3,000원')).toBeInTheDocument();
    expect(screen.getByText('27,000원')).toBeInTheDocument();
  });

  it('할인 금액이 0이면 0원으로 보여준다', () => {
    // given & when
    render(<OrderPriceSummary totalAmount={30000} discountAmount={0} finalAmount={30000} />);

    // then
    expect(screen.getByText('0원')).toBeInTheDocument();
  });

  it('쿠폰이 적용되면 라벨을 쿠폰 할인으로 바꾸고 쿠폰명을 함께 보여준다', () => {
    // given & when
    render(
      <OrderPriceSummary
        totalAmount={30000}
        discountAmount={3000}
        finalAmount={27000}
        couponName="3천원 할인 쿠폰"
      />,
    );

    // then
    expect(screen.getByText('쿠폰 할인')).toBeInTheDocument();
    expect(screen.getByText('3천원 할인 쿠폰')).toBeInTheDocument();
    expect(screen.queryByText('할인 금액')).not.toBeInTheDocument();
  });
});
