import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { cancelOrder, cancelOrderItem } from '@/api/order';
import { ToastProvider } from '@/components/common/Toast';
import { OrderItemClaimActions } from '@/components/order/OrderItemClaimActions';
import type { OrderItem } from '@/types/order';

vi.mock('@/api/order', () => ({
  cancelOrder: vi.fn().mockResolvedValue({}),
  cancelOrderItem: vi.fn().mockResolvedValue({}),
  returnOrderItem: vi.fn().mockResolvedValue({}),
  withdrawOrderClaim: vi.fn().mockResolvedValue({}),
  confirmOrderItem: vi.fn().mockResolvedValue({}),
}));

afterEach(() => {
  vi.clearAllMocks();
});

const baseItem: OrderItem = {
  id: 11,
  productId: 1,
  productName: '레코드 판',
  price: 10000,
  quantity: 1,
  lineAmount: 10000,
  thumbnailUrl: null,
  productOrderNumber: 'ORD-1-01',
  status: 'PAID',
  paidAmount: 10000,
  availableActions: ['CANCEL'],
};

const renderActions = (item: OrderItem) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <OrderItemClaimActions orderId={1} item={item} />
      </ToastProvider>
    </QueryClientProvider>,
  );
};

describe('OrderItemClaimActions', () => {
  it('입금대기 상품의 취소를 확정하면 주문 취소 API 를 부르고 상품 취소 API 는 부르지 않는다', async () => {
    // given
    const user = userEvent.setup();
    renderActions({ ...baseItem, status: 'PAYMENT_WAITING' });

    // when
    await user.click(screen.getByRole('button', { name: '상품 취소' }));
    const dialog = screen.getByRole('dialog', { name: '주문을 취소하시겠습니까?' });
    expect(within(dialog).getByText('입금 전 주문은 주문 전체가 취소됩니다.')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: '상품 취소' }));

    // then
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledWith(1, undefined));
    expect(cancelOrderItem).not.toHaveBeenCalled();
  });

  it('결제완료 상품의 취소를 확정하면 상품 취소 API 를 부르고 주문 취소 API 는 부르지 않는다', async () => {
    // given
    const user = userEvent.setup();
    renderActions(baseItem);

    // when
    await user.click(screen.getByRole('button', { name: '상품 취소' }));
    const dialog = screen.getByRole('dialog', { name: '상품을 취소하시겠습니까?' });
    await user.click(within(dialog).getByRole('button', { name: '상품 취소' }));

    // then
    await waitFor(() => expect(cancelOrderItem).toHaveBeenCalledWith(1, 11, undefined));
    expect(cancelOrder).not.toHaveBeenCalled();
  });
});
