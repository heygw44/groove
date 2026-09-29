import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { useCancelOrder } from '@/hooks/mutations/useOrderMutations';
import { useOrder } from '@/hooks/queries/useOrder';
import OrderDetailPage from '@/pages/order/OrderDetailPage';
import type { OrderDetail } from '@/types/order';

vi.mock('@/hooks/mutations/useOrderMutations', () => ({
  useCancelOrder: vi.fn(),
  useCancelOrderItem: () => ({ mutate: vi.fn(), isPending: false }),
  useReturnOrderItem: () => ({ mutate: vi.fn(), isPending: false }),
  useWithdrawOrderClaim: () => ({ mutate: vi.fn(), isPending: false }),
  useConfirmOrderItem: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock('@/hooks/queries/useOrder', () => ({
  useOrder: vi.fn(),
}));

vi.mock('@/hooks/mutations/useCartMutations', () => ({
  useAddCartItem: () => ({ mutate: vi.fn(), isPending: false }),
}));

const buildOrder = (overrides: Partial<OrderDetail> = {}): OrderDetail => ({
  id: 1,
  orderNumber: 'ORD-1',
  status: 'PAID',
  totalAmount: 10000,
  discountAmount: 0,
  finalAmount: 10000,
  items: [
    {
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
      availableActions: [],
    },
  ],
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
    easyPayProvider: null,
    virtualAccount: null,
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
  it('주문 상품 카드에 재구매 액션을 보여준다', () => {
    // given
    mockOrder(buildOrder());
    mockCancelMutation(buildOrder());

    // when
    renderPage();

    // then
    expect(screen.getByText('레코드 판')).toBeInTheDocument();
    expect(screen.getByText('ORD-1-01')).toBeInTheDocument();
    expect(screen.getByText('결제 금액 10,000원')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '장바구니 담기' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '바로 구매하기' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '리뷰 쓰기' })).not.toBeInTheDocument();
  });

  it('상품주문 availableActions 에 WRITE_REVIEW 가 있으면 리뷰 쓰기 링크를 보여준다', () => {
    // given
    const order = buildOrder({
      status: 'PAID',
      items: [
        {
          id: 11,
          productId: 1,
          productName: '레코드 판',
          price: 10000,
          quantity: 1,
          lineAmount: 10000,
          thumbnailUrl: null,
          productOrderNumber: 'ORD-1-01',
          status: 'DELIVERED',
          paidAmount: 10000,
          availableActions: ['WRITE_REVIEW'],
        },
      ],
    });
    mockOrder(order);
    mockCancelMutation(order);

    // when
    renderPage();

    // then
    const reviewLink = screen.getByRole('link', { name: '리뷰 쓰기' });
    expect(reviewLink).toHaveAttribute('href', '/products/1#reviews');
  });

  it('가상계좌 입금대기(WAITING_FOR_DEPOSIT)면 계좌 안내와 입금대기 배지를 보여준다', () => {
    // given
    const order = buildOrder({
      status: 'PENDING',
      payment: {
        paymentId: 2,
        method: '가상계좌',
        status: 'WAITING_FOR_DEPOSIT',
        amount: 10000,
        approvedAt: '',
        easyPayProvider: null,
        virtualAccount: {
          bankCode: '020',
          accountNumber: '110123456789',
          customerName: '김그루브',
          dueDate: '2026-09-15T23:59:59',
        },
      },
    });
    mockOrder(order);
    mockCancelMutation(order);

    // when
    renderPage();

    // then
    expect(screen.getByText('110123456789')).toBeInTheDocument();
    expect(screen.getAllByText('입금대기').length).toBeGreaterThan(0);
  });

  it('결제 취소 처리 중이면 상태를 표시하고 주문 취소를 비활성화한다', () => {
    // given
    const order = buildOrder({
      payment: {
        paymentId: 1,
        method: '카드',
        status: 'CANCEL_REQUESTED',
        amount: 10000,
        approvedAt: '2026-09-13T00:01:00',
        easyPayProvider: null,
        virtualAccount: null,
      },
    });
    mockOrder(order);
    mockCancelMutation(order);

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
        easyPayProvider: null,
        virtualAccount: null,
      },
    });
    mockOrder(order);
    mockCancelMutation(response);

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
