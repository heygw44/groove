import { describe, expect, it } from 'vitest';

import type { OrderPayment, PaymentStatus } from '@/types/payment';
import {
  PAYMENT_STATUSES,
  PAYMENT_STATUS_BADGE,
  PAYMENT_STATUS_LABEL,
  getPaymentMethodLabel,
  isReconcilePending,
  requiresRefundAccount,
} from '@/utils/paymentStatus';

const buildPayment = (overrides: Partial<OrderPayment> = {}): OrderPayment => ({
  paymentId: 1,
  method: '카드',
  status: 'DONE',
  amount: 10000,
  approvedAt: '2026-09-13T00:01:00',
  easyPayProvider: null,
  virtualAccount: null,
  ...overrides,
});

describe('PAYMENT_STATUS_LABEL', () => {
  it.each(PAYMENT_STATUSES)('%s 상태의 한글 라벨을 갖는다', (status) => {
    // given & when
    const label = PAYMENT_STATUS_LABEL[status];

    // then
    expect(label).toBeTruthy();
  });
});

describe('PAYMENT_STATUS_LABEL / PAYMENT_STATUS_BADGE PARTIAL_CANCELED', () => {
  it('PARTIAL_CANCELED 는 부분취소 라벨과 accent 배지를 갖는다', () => {
    // given & when & then
    expect(PAYMENT_STATUS_LABEL.PARTIAL_CANCELED).toBe('부분취소');
    expect(PAYMENT_STATUS_BADGE.PARTIAL_CANCELED).toBe('accent');
  });
});

describe('isReconcilePending()', () => {
  it.each<[PaymentStatus, boolean]>([
    ['READY', false],
    ['DONE', false],
    ['PARTIAL_CANCELED', false],
    ['CANCELED', false],
    ['FAILED', false],
    ['UNKNOWN', true],
    ['CANCEL_REQUESTED', true],
  ])('%s 상태는 대사 대기 여부가 %s 이다', (status, expected) => {
    // given & when
    const result = isReconcilePending(status);

    // then
    expect(result).toBe(expected);
  });
});

describe('getPaymentMethodLabel()', () => {
  it('결제 이력이 없으면 결제 전을 보여준다', () => {
    // given & when & then
    expect(getPaymentMethodLabel(undefined)).toBe('결제 전');
  });

  it('간편결제 제공자가 있으면 그 값을 그대로 보여준다', () => {
    // given
    const payment = buildPayment({ easyPayProvider: '네이버페이' });

    // when & then
    expect(getPaymentMethodLabel(payment)).toBe('네이버페이');
  });

  it('가상계좌이고 입금대기면 무통장입금 뒤에 입금대기를 붙인다', () => {
    // given
    const payment = buildPayment({
      status: 'WAITING_FOR_DEPOSIT',
      virtualAccount: {
        bankCode: '020',
        accountNumber: '110123456789',
        customerName: '그루브',
        dueDate: '2026-09-15T00:00:00',
      },
    });

    // when & then
    expect(getPaymentMethodLabel(payment)).toBe('무통장입금 (입금대기)');
  });

  it('가상계좌이고 입금완료면 무통장입금만 보여준다', () => {
    // given
    const payment = buildPayment({
      status: 'DONE',
      virtualAccount: {
        bankCode: '020',
        accountNumber: '110123456789',
        customerName: '그루브',
        dueDate: '2026-09-15T00:00:00',
      },
    });

    // when & then
    expect(getPaymentMethodLabel(payment)).toBe('무통장입금');
  });

  it('간편결제도 가상계좌도 아니면 원문 결제수단을 보여준다', () => {
    // given
    const payment = buildPayment({ method: '카드' });

    // when & then
    expect(getPaymentMethodLabel(payment)).toBe('카드');
  });
});

describe('requiresRefundAccount()', () => {
  const virtualAccount = {
    bankCode: '020',
    accountNumber: '110123456789',
    customerName: '그루브',
    dueDate: '2026-09-15T00:00:00',
  };

  it.each([
    ['DONE', true],
    ['PARTIAL_CANCELED', true],
    ['WAITING_FOR_DEPOSIT', false],
    ['CANCELED', false],
    ['CANCEL_REQUESTED', false],
  ] as const)('가상계좌 결제가 %s 이면 %s 를 반환한다', (status, expected) => {
    // when & then
    expect(requiresRefundAccount(buildPayment({ status, virtualAccount }))).toBe(expected);
  });

  it('가상계좌가 아니거나 결제 정보가 없으면 false 를 반환한다', () => {
    // when & then
    expect(requiresRefundAccount(buildPayment({ status: 'DONE' }))).toBe(false);
    expect(requiresRefundAccount(undefined)).toBe(false);
  });
});
