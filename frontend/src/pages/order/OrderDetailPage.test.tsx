import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { useCancelOrder } from '@/hooks/mutations/useOrderMutations';
import { useOrder } from '@/hooks/queries/useOrder';
import { useServerNow } from '@/hooks/useServerNow';
import OrderDetailPage from '@/pages/order/OrderDetailPage';
import type { OrderDetail } from '@/types/order';

vi.mock('@/hooks/mutations/useOrderMutations', () => ({
  useCancelOrder: vi.fn(),
}));

vi.mock('@/hooks/queries/useOrder', () => ({
  useOrder: vi.fn(),
}));

vi.mock('@/hooks/useServerNow', () => ({
  useServerNow: vi.fn(),
}));

const buildOrder = (overrides: Partial<OrderDetail> = {}): OrderDetail => ({
  id: 1,
  orderNumber: 'ORD-1',
  status: 'PAID',
  totalAmount: 10000,
  discountAmount: 0,
  finalAmount: 10000,
  items: [],
  shippingAddress: {
    recipientName: '김그루브',
    phone: '010-0000-0000',
    zipCode: '00000',
    address1: '서울시 어딘가',
  },
  createdAt: '2026-09-13T00:00:00',
  expiresAt: '2026-09-13T00:30:00',
  payment: {
    paymentId: 1,
    method: '카드',
    status: 'DONE',
    amount: 10000,
    approvedAt: '2026-09-13T00:01:00',
  },
  ...overrides,
});

type CancelOrderMutation = ReturnType<typeof useCancelOrder>;

const mockOrder = (order: OrderDetail) => {
  vi.mocked(useOrder).mockReturnValue({
    data: order,
    isPending: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useOrder>);
};

const mockCancelMutation = (response: OrderDetail) => {
  const mutate = ((
    _variables: { orderId: number; reason?: string },
    options?: { onSuccess?: (data: OrderDetail) => void },
  ) => {
    options?.onSuccess?.(response);
  }) as CancelOrderMutation['mutate'];
  vi.mocked(useCancelOrder).mockReturnValue({ mutate, isPending: false } as CancelOrderMutation);
};

const renderPage = () =>
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/orders/1']}>
        <Routes>
          <Route path="/orders/:id" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  );

afterEach(() => {
  vi.clearAllMocks();
});

describe('OrderDetailPage', () => {
  it('결제 취소 처리 중이면 상태를 표시하고 주문 취소를 비활성화한다', () => {
    // given
    const order = buildOrder({
      payment: {
        paymentId: 1,
        method: '카드',
        status: 'CANCEL_REQUESTED',
        amount: 10000,
        approvedAt: '2026-09-13T00:01:00',
      },
    });
    mockOrder(order);
    mockCancelMutation(order);
    vi.mocked(useServerNow).mockReturnValue(0);

    // when
    renderPage();

    // then
    expect(screen.getByText('취소 처리 중')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '주문 취소' })).toBeDisabled();
    expect(
      screen.getByText('취소 결과를 확인하고 있어 다시 취소할 수 없습니다.'),
    ).toBeInTheDocument();
  });

  it('취소 성공 응답이 CANCEL_REQUESTED 면 접수 안내 토스트를 보여준다', async () => {
    // given
    const user = userEvent.setup();
    const order = buildOrder();
    const response = buildOrder({
      payment: {
        paymentId: 1,
        method: '카드',
        status: 'CANCEL_REQUESTED',
        amount: 10000,
        approvedAt: '2026-09-13T00:01:00',
      },
    });
    mockOrder(order);
    mockCancelMutation(response);
    vi.mocked(useServerNow).mockReturnValue(0);

    // when
    renderPage();
    await user.click(screen.getByRole('button', { name: '주문 취소' }));
    const dialog = screen.getByRole('dialog', { name: '주문을 취소하시겠습니까?' });
    await user.click(within(dialog).getByRole('button', { name: '주문 취소' }));

    // then
    expect(
      screen.getByText('취소 요청이 접수됐습니다. 환불 확인까지 잠시 걸릴 수 있습니다.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('주문을 취소했습니다.')).not.toBeInTheDocument();
  });
});
