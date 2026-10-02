import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { useOrder } from '@/hooks/queries/useOrder';
import OrderCompletePage from '@/pages/order/OrderCompletePage';
import type { OrderDetail } from '@/types/order';

vi.mock('@/hooks/queries/useOrder', () => ({
  useOrder: vi.fn(),
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
      productName: '앨범',
      price: 10000,
      quantity: 1,
      lineAmount: 10000,
      thumbnailUrl: null,
      productOrderNumber: 'ORD-1-01',
      status: 'PAID',
      paidAmount: 10000,
      availableActions: [],
      refundInProgress: false,
    },
  ],
  shippingAddress: {
    recipientName: '김그루브',
    phone: '010-0000-0000',
    zipCode: '00000',
    address1: '서울시 어딘가',
  },
  createdAt: '2026-09-28T00:00:00',
  expiresAt: '2026-09-28T00:30:00',
  ...overrides,
});

const mockOrder = (order: OrderDetail | undefined, isPending = false) => {
  vi.mocked(useOrder).mockReturnValue({
    data: order,
    isPending,
    isError: false,
    error: null,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useOrder>);
};

const renderPage = () =>
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/orders/1/complete']}>
        <Routes>
          <Route path="/orders/:id/complete" element={<OrderCompletePage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  );

afterEach(() => {
  vi.clearAllMocks();
});

describe('OrderCompletePage', () => {
  it('카드 결제는 결제수단과 결제금액을 보여주고 계좌 안내는 없다', () => {
    // given
    mockOrder(
      buildOrder({
        payment: {
          paymentId: 1,
          method: '카드',
          status: 'DONE',
          amount: 10000,
          approvedAt: '2026-09-28T00:01:00',
          easyPayProvider: null,
          virtualAccount: null,
        },
      }),
    );

    // when
    renderPage();

    // then
    expect(screen.getByText('주문이 완료되었습니다')).toBeInTheDocument();
    expect(screen.getByText('ORD-1')).toBeInTheDocument();
    expect(screen.getByText('카드')).toBeInTheDocument();
    expect(screen.queryByText('무통장입금 계좌 안내')).not.toBeInTheDocument();
  });

  it('간편결제는 easyPayProvider 를 결제수단으로 보여준다', () => {
    // given
    mockOrder(
      buildOrder({
        payment: {
          paymentId: 1,
          method: '간편결제',
          status: 'DONE',
          amount: 10000,
          approvedAt: '2026-09-28T00:01:00',
          easyPayProvider: '네이버페이',
          virtualAccount: null,
        },
      }),
    );

    // when
    renderPage();

    // then
    expect(screen.getByText('네이버페이')).toBeInTheDocument();
  });

  it('입금대기(가상계좌)는 은행·계좌번호·입금기한 안내를 보여준다', () => {
    // given
    mockOrder(
      buildOrder({
        status: 'PENDING',
        payment: {
          paymentId: 1,
          method: '가상계좌',
          status: 'WAITING_FOR_DEPOSIT',
          amount: 10000,
          approvedAt: '2026-09-28T00:01:00',
          easyPayProvider: null,
          virtualAccount: {
            bankCode: '88',
            accountNumber: '110-1234-5678',
            customerName: '김그루브',
            dueDate: '2026-09-30T23:59:59',
          },
        },
      }),
    );

    // when
    renderPage();

    // then
    expect(
      screen.getByText('주문이 접수되었습니다. 입금을 기다리고 있습니다.'),
    ).toBeInTheDocument();
    expect(screen.getByText('무통장입금 계좌 안내')).toBeInTheDocument();
    expect(screen.getByText('신한은행')).toBeInTheDocument();
    expect(screen.getByText('110-1234-5678')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '복사' })).toBeInTheDocument();
  });

  it('결제정보가 아직 없으면 확인 중 안내를 보여준다', () => {
    // given
    mockOrder(buildOrder({ payment: undefined }));

    // when
    renderPage();

    // then
    expect(
      screen.getByText('결제 결과를 확인하고 있습니다. 잠시 후 다시 확인해주세요.'),
    ).toBeInTheDocument();
  });

  it('취소된 주문이면 완료 대신 취소 안내를 제목으로 보여준다', () => {
    // given
    mockOrder(buildOrder({ status: 'CANCELED', payment: undefined }));

    // when
    renderPage();

    // then
    expect(screen.getByText('취소된 주문입니다')).toBeInTheDocument();
    expect(screen.queryByText('주문이 완료되었습니다')).not.toBeInTheDocument();
  });
});
