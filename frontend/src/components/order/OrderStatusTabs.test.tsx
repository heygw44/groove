import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { OrderStatusTabs } from '@/components/order/OrderStatusTabs';

describe('OrderStatusTabs', () => {
  it('네이버식 상태 탭 7개와 전체 탭을 보여준다', () => {
    // given & when
    render(<OrderStatusTabs onChange={vi.fn()} />);

    // then
    [
      '전체',
      '입금대기',
      '결제완료',
      '배송준비',
      '배송중',
      '배송완료',
      '구매확정',
      '취소·반품',
    ].forEach((label) => {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument();
    });
  });

  it('탭을 누르면 해당 statusGroup 으로 onChange 를 호출한다', async () => {
    // given
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<OrderStatusTabs onChange={onChange} />);

    // when
    await user.click(screen.getByRole('button', { name: '취소·반품' }));

    // then
    expect(onChange).toHaveBeenCalledWith('CANCEL_RETURN');
  });

  it('전체 탭을 누르면 statusGroup 없이 onChange 를 호출한다', async () => {
    // given
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<OrderStatusTabs value="PAID" onChange={onChange} />);

    // when
    await user.click(screen.getByRole('button', { name: '전체' }));

    // then
    expect(onChange).toHaveBeenCalledWith(undefined);
  });
});
