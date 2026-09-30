import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { cancelOrder, cancelOrderItem } from '@/api/order';
import { ToastProvider } from '@/components/common/Toast';
import { OrderItemClaimActions } from '@/components/order/OrderItemClaimActions';
import type { OrderDetail, OrderItem } from '@/types/order';
import type { OrderPayment } from '@/types/payment';

vi.mock('@/api/order', () => ({
  cancelOrder: vi.fn().mockResolvedValue({ items: [] }),
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
  refundInProgress: false,
};

const renderActions = (item: OrderItem, payment?: OrderPayment) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <OrderItemClaimActions orderId={1} item={item} payment={payment} />
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
    await user.click(screen.getByRole('button', { name: '주문취소' }));
    const dialog = screen.getByRole('dialog', { name: '주문을 취소하시겠습니까?' });
    expect(within(dialog).getByText('입금 전 주문은 주문 전체가 취소됩니다.')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: '주문취소' }));

    // then
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledWith(1, undefined));
    expect(cancelOrderItem).not.toHaveBeenCalled();
  });

  it('결제완료 상품의 취소를 확정하면 상품 취소 API 를 부르고 주문 취소 API 는 부르지 않는다', async () => {
    // given
    const user = userEvent.setup();
    renderActions(baseItem);

    // when
    await user.click(screen.getByRole('button', { name: '주문취소' }));
    const dialog = screen.getByRole('dialog', { name: '주문을 취소하시겠습니까?' });
    await user.click(within(dialog).getByRole('button', { name: '주문취소' }));

    // then
    await waitFor(() => expect(cancelOrderItem).toHaveBeenCalledWith(1, 11, undefined));
    expect(cancelOrder).not.toHaveBeenCalled();
  });

  it('입금대기가 아닌 즉시 취소 버튼 이름은 주문취소다', () => {
    // given & when
    renderActions(baseItem);

    // then
    expect(screen.getByRole('button', { name: '주문취소' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '상품 취소' })).not.toBeInTheDocument();
  });

  it('주문 취소 응답이 CANCEL_REQUESTED 면 접수 안내 토스트를 보여준다', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(cancelOrder).mockResolvedValueOnce({
      items: [],
      payment: { status: 'CANCEL_REQUESTED' },
    } as unknown as OrderDetail);
    renderActions({ ...baseItem, status: 'PAYMENT_WAITING' });

    // when
    await user.click(screen.getByRole('button', { name: '주문취소' }));
    const dialog = screen.getByRole('dialog', { name: '주문을 취소하시겠습니까?' });
    await user.click(within(dialog).getByRole('button', { name: '주문취소' }));

    // then
    expect(
      await screen.findByText('취소 요청이 접수됐습니다. 환불 확인까지 잠시 걸릴 수 있습니다.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('주문을 취소했습니다.')).not.toBeInTheDocument();
  });

  it('결제 취소 결과를 확인하는 중이면 주문취소 버튼을 비활성화한다', () => {
    // given & when
    renderActions(baseItem, {
      paymentId: 1,
      method: '카드',
      status: 'CANCEL_REQUESTED',
      amount: 10000,
      approvedAt: '2026-09-13T00:01:00',
      easyPayProvider: null,
      virtualAccount: null,
    });

    // then
    expect(screen.getByRole('button', { name: '주문취소' })).toBeDisabled();
  });

  it('환불 결과가 미확정인 상품이면 환불 처리 중 안내를 보여준다', () => {
    // given & when
    renderActions({ ...baseItem, availableActions: [], refundInProgress: true });

    // then
    expect(screen.getByText('환불 처리 중')).toBeInTheDocument();
    expect(screen.getByText(/결제사 환불 결과를 확인하고 있습니다/)).toBeInTheDocument();
  });

  it('환불이 미확정이 아니면 환불 처리 중 안내를 보이지 않는다', () => {
    // given & when
    renderActions(baseItem);

    // then
    expect(screen.queryByText('환불 처리 중')).not.toBeInTheDocument();
  });

  it('주문 취소 응답에 환불 미확정 상품이 있으면 나머지 상품 재취소 안내 토스트를 보여준다', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(cancelOrder).mockResolvedValueOnce({
      items: [{ ...baseItem, refundInProgress: true }],
      payment: { status: 'DONE' },
    } as unknown as OrderDetail);
    renderActions({ ...baseItem, status: 'PAYMENT_WAITING' });

    // when
    await user.click(screen.getByRole('button', { name: '주문취소' }));
    const dialog = screen.getByRole('dialog', { name: '주문을 취소하시겠습니까?' });
    await user.click(within(dialog).getByRole('button', { name: '주문취소' }));

    // then
    expect(
      await screen.findByText(
        '일부 상품의 환불 결과를 확인하고 있습니다. 확인되면 나머지 상품을 다시 취소해 주세요.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText('주문을 취소했습니다.')).not.toBeInTheDocument();
  });
});
