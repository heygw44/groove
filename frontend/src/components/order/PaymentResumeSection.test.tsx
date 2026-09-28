import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { PaymentResumeSection } from '@/components/order/PaymentResumeSection';
import { usePaymentWindow } from '@/hooks/usePaymentWindow';
import { useAuthStore } from '@/store/authStore';
import type { OrderDetail } from '@/types/order';

vi.mock('@/hooks/usePaymentWindow', () => ({
  usePaymentWindow: vi.fn(),
}));

const buildOrder = (overrides: Partial<OrderDetail> = {}): OrderDetail => ({
  id: 1,
  orderNumber: 'ORD-1',
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

const openPaymentWindow = vi.fn();

beforeEach(() => {
  vi.mocked(usePaymentWindow).mockReturnValue({ openPaymentWindow, isOpening: false });
  useAuthStore.setState({ accessToken: 'token', member: null, isBootstrapping: false });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('PaymentResumeSection', () => {
  it('PENDING 이 아니면 아무것도 그리지 않는다', () => {
    // given & when
    const { container } = render(
      <PaymentResumeSection order={buildOrder({ status: 'PAID' })} disabled={false} />,
    );

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it('입금대기(WAITING_FOR_DEPOSIT)면 아무것도 그리지 않는다', () => {
    // given & when
    const { container } = render(
      <PaymentResumeSection
        order={buildOrder({
          payment: {
            paymentId: 1,
            method: '가상계좌',
            status: 'WAITING_FOR_DEPOSIT',
            amount: 10000,
            approvedAt: '2026-09-28T00:01:00',
            easyPayProvider: null,
            virtualAccount: {
              bankCode: '88',
              accountNumber: '110-1',
              customerName: null,
              dueDate: '2026-09-30T23:59:59',
            },
          },
        })}
        disabled={false}
      />,
    );

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it('PENDING(카드/간편결제 대기)이면 결제 이어하기 버튼을 보여주고 누르면 결제수단 선택이 나온다', async () => {
    // given
    const user = userEvent.setup();
    render(<PaymentResumeSection order={buildOrder()} disabled={false} />);

    // when
    await user.click(screen.getByRole('button', { name: '결제 이어하기' }));

    // then
    expect(screen.getByRole('radio', { name: '신용·체크카드' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /결제하기/ })).toBeInTheDocument();
  });

  it('결제하기를 누르면 openPaymentWindow 를 주문 정보로 호출한다', async () => {
    // given
    const user = userEvent.setup();
    render(<PaymentResumeSection order={buildOrder()} disabled={false} />);
    await user.click(screen.getByRole('button', { name: '결제 이어하기' }));

    // when
    await user.click(screen.getByRole('button', { name: /결제하기/ }));

    // then
    expect(openPaymentWindow).toHaveBeenCalledWith(
      expect.objectContaining({ orderId: 1, orderNumber: 'ORD-1', method: 'CARD' }),
    );
  });
});
