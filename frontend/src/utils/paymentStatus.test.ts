import { describe, expect, it } from 'vitest';

import type { OrderDetail, OrderItem } from '@/types/order';
import type { OrderPayment } from '@/types/payment';
import {
  PAYMENT_STATUSES,
  PAYMENT_STATUS_BADGE,
  PAYMENT_STATUS_LABEL,
  getAdminClaimApproveMessage,
  getAdminClaimCompleteMessage,
  getAdminSaleCancelMessage,
  getOrderCancelSuccessMessage,
  getOrderItemCancelSuccessMessage,
  getPaymentMethodLabel,
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

  it('가상계좌가 아니거나 결제정보가 없으면 false 를 반환한다', () => {
    // when & then
    expect(requiresRefundAccount(buildPayment({ status: 'DONE' }))).toBe(false);
    expect(requiresRefundAccount(undefined)).toBe(false);
  });
});

const buildItem = (overrides: Partial<OrderItem> = {}): OrderItem =>
  ({ status: 'PAID', availableActions: [], refundInProgress: false, ...overrides }) as OrderItem;

describe('getOrderItemCancelSuccessMessage()', () => {
  it.each([
    [{ status: 'CANCELED' }, '주문을 취소했습니다.'],
    [
      { status: 'PAID', refundInProgress: true },
      '취소요청이 접수되었습니다. 환불까지 시간이 조금 걸릴 수 있습니다.',
    ],
    [{ status: 'PREPARING' }, '취소요청이 접수되었습니다. 승인되면 취소됩니다.'],
  ] as const)('%j 응답이면 "%s" 를 돌려준다', (overrides, expected) => {
    // when & then
    expect(getOrderItemCancelSuccessMessage(buildItem(overrides))).toBe(expected);
  });
});

describe('관리자 클레임 처리 메시지', () => {
  it('승인은 CANCELED 일 때만 승인 완료로 안내한다', () => {
    // when & then
    expect(getAdminClaimApproveMessage({ status: 'CANCELED' })).toBe('취소요청을 승인했습니다.');
    expect(getAdminClaimApproveMessage({ status: 'PREPARING' })).toContain('환불 결과를 확인');
  });

  it('수거 완료는 RETURNED 일 때만 완료로 안내한다', () => {
    // when & then
    expect(getAdminClaimCompleteMessage({ status: 'RETURNED' })).toBe('반품 수거를 완료했습니다.');
    expect(getAdminClaimCompleteMessage({ status: 'DELIVERED' })).toContain('환불 결과를 확인');
  });

  it('판매취소는 CANCELED 일 때만 처리 완료로 안내한다', () => {
    // when & then
    expect(getAdminSaleCancelMessage({ status: 'CANCELED' }, 'ORD-1-01')).toBe(
      'ORD-1-01 판매취소 처리했습니다.',
    );
    expect(getAdminSaleCancelMessage({ status: 'PAID' }, 'ORD-1-01')).toContain('환불 결과를 확인');
  });
});

describe('getOrderCancelSuccessMessage()', () => {
  it('취소 가능한 상품이 남아 있으면 일부 취소 안내를 돌려준다', () => {
    // given
    const order = {
      items: [buildItem({ status: 'CANCELED' }), buildItem({ availableActions: ['CANCEL'] })],
    } as OrderDetail;

    // when & then
    expect(getOrderCancelSuccessMessage(order)).toBe(
      '일부 상품만 취소되었습니다. 남은 상품을 확인해주세요.',
    );
  });

  it('남은 취소 액션이 없으면 취소완료로 안내한다', () => {
    // given
    const order = { items: [buildItem({ status: 'CANCELED' })] } as OrderDetail;

    // when & then
    expect(getOrderCancelSuccessMessage(order)).toBe('주문을 취소했습니다.');
  });
});
