import { describe, expect, it } from 'vitest';

import {
  ORDER_STATUS_BADGE,
  ORDER_STATUS_LABEL,
  ORDER_STATUSES,
  formatCancelReason,
  getOrderDisplayStatus,
  isOrderStatus,
} from '@/utils/orderStatus';
import { PAYMENT_STATUS_BADGE, PAYMENT_STATUS_LABEL } from '@/utils/paymentStatus';

describe('ORDER_STATUS_LABEL', () => {
  it.each(ORDER_STATUSES)('%s 상태의 한글 라벨을 갖는다', (status) => {
    // given & when
    const label = ORDER_STATUS_LABEL[status];

    // then
    expect(label).toBeTruthy();
  });
});

describe('isOrderStatus()', () => {
  it('유효한 주문 상태 문자열이면 true 를 반환한다', () => {
    // given & when & then
    expect(isOrderStatus('PAID')).toBe(true);
  });

  it('알 수 없는 문자열이면 false 를 반환한다', () => {
    // given & when & then
    expect(isOrderStatus('UNKNOWN')).toBe(false);
  });

  it('문자열이 아니면 false 를 반환한다', () => {
    // given & when & then
    expect(isOrderStatus(1)).toBe(false);
    expect(isOrderStatus(undefined)).toBe(false);
  });
});

describe('getOrderDisplayStatus()', () => {
  it('PENDING 이고 결제가 WAITING_FOR_DEPOSIT 이면 입금대기로 파생 표시한다', () => {
    // given & when
    const result = getOrderDisplayStatus('PENDING', 'WAITING_FOR_DEPOSIT');

    // then
    expect(result).toEqual({
      label: PAYMENT_STATUS_LABEL.WAITING_FOR_DEPOSIT,
      variant: PAYMENT_STATUS_BADGE.WAITING_FOR_DEPOSIT,
    });
  });

  it('PENDING 이어도 결제상태가 WAITING_FOR_DEPOSIT 이 아니면 원래 라벨을 유지한다', () => {
    // given & when
    const result = getOrderDisplayStatus('PENDING', 'READY');

    // then
    expect(result).toEqual({
      label: ORDER_STATUS_LABEL.PENDING,
      variant: ORDER_STATUS_BADGE.PENDING,
    });
  });

  it('결제상태가 없어도 원래 주문 상태 라벨을 돌려준다', () => {
    // given & when
    const result = getOrderDisplayStatus('PAID');

    // then
    expect(result).toEqual({ label: ORDER_STATUS_LABEL.PAID, variant: ORDER_STATUS_BADGE.PAID });
  });

  it('PENDING 이 아니면 결제가 WAITING_FOR_DEPOSIT 이어도 원래 주문 상태 라벨을 유지한다', () => {
    // given & when
    const result = getOrderDisplayStatus('PAID', 'WAITING_FOR_DEPOSIT');

    // then
    expect(result).toEqual({ label: ORDER_STATUS_LABEL.PAID, variant: ORDER_STATUS_BADGE.PAID });
  });
});

describe('formatCancelReason()', () => {
  it.each([
    ['EXPIRED', '입금기한 만료'],
    ['SUPERSEDED', '다른 결제로 변경'],
  ])('시스템 사유 %s 는 %s 로 바꾼다', (reason, label) => {
    // given & when
    const result = formatCancelReason(reason);

    // then
    expect(result).toBe(label);
  });

  it.each(['단순 변심', 'constructor'])('구매자가 입력한 사유 %s 는 그대로 돌려준다', (reason) => {
    // given & when
    const result = formatCancelReason(reason);

    // then
    expect(result).toBe(reason);
  });
});
