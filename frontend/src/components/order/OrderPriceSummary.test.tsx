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
});
