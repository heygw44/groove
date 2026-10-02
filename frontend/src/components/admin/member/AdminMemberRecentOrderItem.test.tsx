import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AdminMemberRecentOrderItem } from '@/components/admin/member/AdminMemberRecentOrderItem';
import type { AdminMemberRecentOrder } from '@/types/adminMember';

const makeOrder = (overrides: Partial<AdminMemberRecentOrder> = {}): AdminMemberRecentOrder => ({
  id: 1,
  orderNumber: 'ORD-1',
  status: 'PAID',
  finalAmount: 50000,
  canceledAmount: 0,
  representativeProductName: 'Kind of Blue',
  itemCount: 1,
  createdAt: '2026-09-05T12:00:00+09:00',
  ...overrides,
});

const renderItem = (order: AdminMemberRecentOrder) =>
  render(
    <MemoryRouter>
      <ul>
        <AdminMemberRecentOrderItem order={order} />
      </ul>
    </MemoryRouter>,
  );

describe('AdminMemberRecentOrderItem', () => {
  it('주문 상태 라벨과 결제 금액을 보여준다', () => {
    // given & when
    renderItem(makeOrder());

    // then
    expect(screen.getByText('결제완료')).toBeInTheDocument();
    expect(screen.getByText('50,000원')).toBeInTheDocument();
    expect(screen.queryByText(/환불/)).not.toBeInTheDocument();
  });

  it('환불액이 있으면 환불액을 뺀 금액과 환불 표기를 보여준다', () => {
    // given & when
    renderItem(makeOrder({ canceledAmount: 20000, itemCount: 3 }));

    // then
    expect(screen.getByText('30,000원', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('(환불 20,000원)')).toBeInTheDocument();
    expect(screen.getByText('Kind of Blue 외 2개')).toBeInTheDocument();
  });

  it('전액 취소된 주문은 취소 라벨과 0원을 보여준다', () => {
    // given & when
    renderItem(makeOrder({ status: 'CANCELED', canceledAmount: 50000 }));

    // then
    expect(screen.getByText('취소')).toBeInTheDocument();
    expect(screen.getByText('(환불 50,000원)')).toBeInTheDocument();
    expect(screen.getByText(/^0원/)).toBeInTheDocument();
  });
});
