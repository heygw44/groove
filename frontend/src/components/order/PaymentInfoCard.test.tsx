import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { PaymentInfoCard } from '@/components/order/PaymentInfoCard';
import type { OrderPayment } from '@/types/payment';

const buildPayment = (overrides: Partial<OrderPayment> = {}): OrderPayment => ({
  paymentId: 1,
  method: '카드',
  status: 'DONE',
  amount: 27000,
  approvedAt: '2026-09-13T00:01:00',
  easyPayProvider: null,
  virtualAccount: null,
  ...overrides,
});

describe('PaymentInfoCard', () => {
  it('결제 전(PENDING)이면 결제수단으로 결제 전을 보여준다', () => {
    // given & when
    render(<PaymentInfoCard totalAmount={30000} discountAmount={3000} finalAmount={27000} />);

    // then
    expect(screen.getByText('결제 전')).toBeInTheDocument();
    expect(screen.getByText('27,000원')).toBeInTheDocument();
  });

  it('간편결제면 easyPayProvider 값을 결제수단 줄에 보여준다', () => {
    // given
    const payment = buildPayment({ easyPayProvider: '네이버페이' });

    // when
    render(
      <PaymentInfoCard
        totalAmount={30000}
        discountAmount={3000}
        finalAmount={27000}
        payment={payment}
      />,
    );

    // then
    expect(screen.getByText('네이버페이')).toBeInTheDocument();
  });

  it('부분취소된 결제도 결제수단을 보여준다', () => {
    // given
    const payment = buildPayment({ status: 'PARTIAL_CANCELED', easyPayProvider: '네이버페이' });

    // when
    render(
      <PaymentInfoCard
        totalAmount={30000}
        discountAmount={3000}
        finalAmount={27000}
        payment={payment}
      />,
    );

    // then
    expect(screen.getByText('네이버페이')).toBeInTheDocument();
    expect(screen.queryByText('결제 전')).not.toBeInTheDocument();
  });

  it('가상계좌 입금대기면 무통장입금 (입금대기)를 보여준다', () => {
    // given
    const payment = buildPayment({
      status: 'WAITING_FOR_DEPOSIT',
      approvedAt: '',
      virtualAccount: {
        bankCode: '020',
        accountNumber: '110123456789',
        customerName: '그루브',
        dueDate: '2026-09-15T00:00:00',
      },
    });

    // when
    render(
      <PaymentInfoCard
        totalAmount={30000}
        discountAmount={3000}
        finalAmount={27000}
        payment={payment}
      />,
    );

    // then
    expect(screen.getByText('무통장입금 (입금대기)')).toBeInTheDocument();
    expect(screen.queryByText('승인 시각')).not.toBeInTheDocument();
  });

  it('카드 결제면 원문 method 를 보여주고 승인 시각을 함께 표시한다', () => {
    // given
    const payment = buildPayment({ method: '카드' });

    // when
    render(
      <PaymentInfoCard
        totalAmount={30000}
        discountAmount={3000}
        finalAmount={27000}
        payment={payment}
      />,
    );

    // then
    expect(screen.getByText('카드')).toBeInTheDocument();
    expect(screen.getByText('승인 시각')).toBeInTheDocument();
  });

  it('취소 처리 중(CANCEL_REQUESTED)이면 결제상태 배지를 함께 보여준다', () => {
    // given
    const payment = buildPayment({ status: 'CANCEL_REQUESTED' });

    // when
    render(
      <PaymentInfoCard
        totalAmount={30000}
        discountAmount={3000}
        finalAmount={27000}
        payment={payment}
      />,
    );

    // then
    expect(screen.getByText('취소 처리 중')).toBeInTheDocument();
  });
});
