import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { AdminOrderItemTable } from '@/components/admin/AdminOrderItemTable';
import type { AdminOrderItemSummary } from '@/types/adminOrder';

const buildItem = (overrides: Partial<AdminOrderItemSummary> = {}): AdminOrderItemSummary => ({
  id: 1,
  orderId: 1,
  productOrderNumber: 'ORD-1-01',
  orderNumber: 'ORD-1',
  memberEmail: 'member@groove.com',
  productName: '레코드',
  quantity: 1,
  status: 'PAID',
  virtualAccountPayment: false,
  createdAt: '2026-09-13T00:00:00',
  ...overrides,
});

const renderTable = (
  item: AdminOrderItemSummary,
  onCancel = vi.fn(),
  selectionDisabled = false,
) => {
  render(
    <AdminOrderItemTable
      items={[item]}
      selectedIds={[]}
      onToggle={vi.fn()}
      onToggleAll={vi.fn()}
      onCancel={onCancel}
      onOpenOrder={vi.fn()}
      selectionDisabled={selectionDisabled}
    />,
  );
  return onCancel;
};

describe('AdminOrderItemTable', () => {
  it('일반 결제 상품은 판매취소 버튼이 활성화돼 있고 안내가 없다', async () => {
    // given
    const user = userEvent.setup();
    const onCancel = renderTable(buildItem());

    // when
    await user.click(screen.getByRole('button', { name: '상품주문 ORD-1-01 판매취소' }));

    // then
    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('구매자 환불계좌 필요')).not.toBeInTheDocument();
  });

  it('가상계좌 결제 상품은 판매취소 버튼이 비활성화되고 안내를 보인다', () => {
    // given & when
    renderTable(buildItem({ virtualAccountPayment: true }));

    // then
    const button = screen.getByRole('button', { name: '상품주문 ORD-1-01 판매취소' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('title', '구매자 환불계좌가 필요합니다');
    expect(screen.getByText('구매자 환불계좌 필요')).toBeInTheDocument();
  });

  it('판매취소할 수 없는 상태면 가상계좌여도 버튼과 안내를 숨긴다', () => {
    // given & when
    renderTable(buildItem({ virtualAccountPayment: true, status: 'SHIPPING' }));

    // then
    expect(screen.queryByRole('button', { name: /판매취소/ })).not.toBeInTheDocument();
    expect(screen.queryByText('구매자 환불계좌 필요')).not.toBeInTheDocument();
  });

  it('상품주문번호 아래에 주문번호 버튼이 함께 보인다', () => {
    // given & when
    renderTable(buildItem());

    // then
    expect(screen.getByText('ORD-1-01')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '주문 ORD-1 상세 보기' })).toHaveTextContent('ORD-1');
    expect(screen.queryByRole('columnheader', { name: '주문번호' })).not.toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', { name: '상품주문번호 / 주문번호' }),
    ).toBeInTheDocument();
  });

  it('긴 이메일은 잘리지만 title 로 전체를 볼 수 있다', () => {
    // given & when
    renderTable(buildItem({ memberEmail: 'very-long-address@groove.com' }));

    // then
    expect(screen.getByTitle('very-long-address@groove.com')).toBeInTheDocument();
  });

  it('selectionDisabled 이면 전체 선택 체크박스가 비활성화된다', () => {
    // given & when
    renderTable(buildItem(), vi.fn(), true);

    // then
    expect(screen.getByRole('checkbox', { name: '현재 페이지 전체 선택' })).toBeDisabled();
  });
});
