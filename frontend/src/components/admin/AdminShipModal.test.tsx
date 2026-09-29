import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { shipAdminOrderItems } from '@/api/admin';
import { AdminShipModal } from '@/components/admin/AdminShipModal';
import type { AdminOrderItemSummary } from '@/types/adminOrder';

vi.mock('@/api/admin', () => ({
  shipAdminOrderItems: vi.fn(),
}));

const buildItem = (id: number): AdminOrderItemSummary => ({
  id,
  orderId: 1,
  productOrderNumber: `ORD-1-0${id}`,
  orderNumber: 'ORD-1',
  memberEmail: 'member@groove.com',
  productName: `레코드 ${id}`,
  quantity: 1,
  status: 'PREPARING',
  createdAt: '2026-09-13T00:00:00',
});

const renderModal = (items: AdminOrderItemSummary[], onCompleted = vi.fn()) => {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <AdminShipModal items={items} onClose={vi.fn()} onCompleted={onCompleted} />
    </QueryClientProvider>,
  );
  return onCompleted;
};

describe('AdminShipModal', () => {
  afterEach(() => vi.clearAllMocks());

  it('송장번호를 모두 입력하기 전에는 발송처리 버튼이 비활성화된다', async () => {
    // given
    const user = userEvent.setup();
    renderModal([buildItem(1), buildItem(2)]);

    // when
    await user.type(screen.getByLabelText(/ORD-1-01/), '1111');

    // then
    expect(screen.getByRole('button', { name: '발송처리' })).toBeDisabled();
  });

  it('택배사와 송장번호를 상품주문별로 담아 발송 요청하고 결과를 넘긴다', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(shipAdminOrderItems).mockResolvedValue({ processed: 2, skipped: 0 });
    const onCompleted = renderModal([buildItem(1), buildItem(2)]);

    // when
    await user.selectOptions(screen.getByLabelText(/택배사/), 'HANJIN');
    await user.type(screen.getByLabelText(/ORD-1-01/), ' 1111 ');
    await user.type(screen.getByLabelText(/ORD-1-02/), '2222');
    await user.click(screen.getByRole('button', { name: '발송처리' }));

    // then
    await vi.waitFor(() => expect(onCompleted).toHaveBeenCalled());
    expect(shipAdminOrderItems).toHaveBeenCalledWith({
      items: [
        { orderItemId: 1, courierCode: 'HANJIN', trackingNumber: '1111' },
        { orderItemId: 2, courierCode: 'HANJIN', trackingNumber: '2222' },
      ],
    });
    expect(onCompleted.mock.calls[0][0]).toEqual({ processed: 2, skipped: 0 });
  });
});
