import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useOrder } from '@/hooks/queries/useOrder';
import { usePaymentWindow } from '@/hooks/usePaymentWindow';
import { useServerNow } from '@/hooks/useServerNow';
import PaymentFailPage from '@/pages/payment/PaymentFailPage';
import { useAuthStore } from '@/store/authStore';
import type { OrderDetail } from '@/types/order';

vi.mock('@/hooks/queries/useOrder', () => ({
  useOrder: vi.fn(),
}));

vi.mock('@/hooks/usePaymentWindow', () => ({
  usePaymentWindow: vi.fn(),
}));

vi.mock('@/hooks/useServerNow', () => ({
  useServerNow: vi.fn(),
}));

const buildOrder = (overrides: Partial<OrderDetail> = {}): OrderDetail => ({
  id: 7,
  orderNumber: 'ORD-7',
  status: 'PENDING',
  totalAmount: 10000,
  discountAmount: 0,
  finalAmount: 10000,
  items: [
    {
      productId: 1,
      productName: '앨범',
      price: 10000,
      quantity: 1,
      lineAmount: 10000,
      thumbnailUrl: null,
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

const mockOrder = (order: OrderDetail | undefined) => {
  vi.mocked(useOrder).mockReturnValue({
    data: order,
    isPending: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useOrder>);
};

const openPaymentWindow = vi.fn();

const renderPage = (search: string) =>
  render(
    <MemoryRouter initialEntries={[`/payments/fail${search}`]}>
      <Routes>
        <Route path="/payments/fail" element={<PaymentFailPage />} />
      </Routes>
    </MemoryRouter>,
  );

beforeEach(() => {
  vi.mocked(usePaymentWindow).mockReturnValue({ openPaymentWindow, isOpening: false });
  vi.mocked(useServerNow).mockReturnValue(new Date('2026-09-28T00:10:00+09:00').getTime());
  useAuthStore.setState({ accessToken: 'token', member: null, isBootstrapping: false });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('PaymentFailPage', () => {
  it('PENDING 이고 기한이 남았으면 결제수단 선택과 다시 결제하기 버튼을 보여준다', () => {
    // given
    mockOrder(buildOrder());

    // when
    renderPage('?code=REJECT_CARD_COMPANY&orderId=ORD-7&orderRef=7');

    // then
    expect(screen.getByRole('button', { name: '다시 결제하기' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: '신용·체크카드' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '주문 내역' })).toBeInTheDocument();
  });

  it('다시 결제하기를 누르면 같은 주문으로 결제창을 연다', async () => {
    // given
    const user = userEvent.setup();
    mockOrder(buildOrder());
    renderPage('?code=REJECT_CARD_COMPANY&orderId=ORD-7&orderRef=7');

    // when
    await user.click(screen.getByRole('button', { name: '다시 결제하기' }));

    // then
    expect(openPaymentWindow).toHaveBeenCalledWith(
      expect.objectContaining({ orderId: 7, orderNumber: 'ORD-7', method: 'CARD' }),
    );
  });

  it('기한이 지났으면 다시 결제하기를 보여주지 않는다', () => {
    // given
    mockOrder(buildOrder({ expiresAt: '2026-09-28T00:05:00' }));

    // when
    renderPage('?code=REJECT_CARD_COMPANY&orderId=ORD-7&orderRef=7');

    // then
    expect(screen.queryByRole('button', { name: '다시 결제하기' })).not.toBeInTheDocument();
  });

  it('orderRef 가 없으면 다시 결제하기를 보여주지 않는다', () => {
    // given
    mockOrder(undefined);

    // when
    renderPage('?code=REJECT_CARD_COMPANY');

    // then
    expect(screen.queryByRole('button', { name: '다시 결제하기' })).not.toBeInTheDocument();
  });
});
