import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { CartSummary } from '@/components/cart/CartSummary';

describe('CartSummary', () => {
  it('선택한 상품이 없으면 주문하기 버튼이 비활성이다', () => {
    // given & when
    render(<CartSummary selectedCount={0} totalAmount={0} onOrder={vi.fn()} />);

    // then
    expect(screen.getByRole('button', { name: '주문하기' })).toBeDisabled();
  });

  it('선택한 상품이 있으면 주문하기 버튼이 활성이다', () => {
    // given & when
    render(<CartSummary selectedCount={2} totalAmount={30000} onOrder={vi.fn()} />);

    // then
    expect(screen.getByRole('button', { name: '주문하기' })).toBeEnabled();
  });

  it('선택 개수와 총 결제 예정 금액을 보여준다', () => {
    // given & when
    render(<CartSummary selectedCount={2} totalAmount={30000} onOrder={vi.fn()} />);

    // then
    expect(screen.getByText('선택 상품 2개')).toBeInTheDocument();
    expect(screen.getByText('총 결제 예정 금액 30,000원')).toBeInTheDocument();
  });

  it('disabled 가 true 이면 선택 상품이 있어도 비활성이다', () => {
    // given & when
    render(<CartSummary selectedCount={2} totalAmount={30000} onOrder={vi.fn()} disabled />);

    // then
    expect(screen.getByRole('button', { name: '주문하기' })).toBeDisabled();
  });
});
