import { describe, expect, it } from 'vitest';

import type { OrderStatus } from '@/types/order';
import {
  ADMIN_ORDER_TRANSITIONS,
  ORDER_STATUS_BADGE,
  ORDER_STATUS_LABEL,
  ORDER_STATUSES,
  getOrderDisplayStatus,
  isCancelableStatus,
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

describe('isCancelableStatus()', () => {
  it.each<[OrderStatus, boolean]>([
    ['PENDING', true],
    ['PAID', true],
    ['PREPARING', false],
    ['SHIPPED', false],
    ['DELIVERED', false],
    ['CANCELED', false],
    ['REFUNDED', false],
  ])('%s 상태는 취소 가능 여부가 %s 이다', (status, expected) => {
    // given & when
    const result = isCancelableStatus(status);

    // then
    expect(result).toBe(expected);
  });
});

describe('ADMIN_ORDER_TRANSITIONS', () => {
  it.each<[OrderStatus, OrderStatus[]]>([
    ['PENDING', []],
    ['PAID', ['PREPARING', 'CANCELED']],
    ['PREPARING', ['SHIPPED', 'CANCELED']],
    ['SHIPPED', ['DELIVERED']],
    ['DELIVERED', []],
    ['CANCELED', []],
    ['REFUNDED', []],
  ])('%s 상태에서 전이 가능한 상태는 %s 이다', (status, expected) => {
    // given & when
    const result = ADMIN_ORDER_TRANSITIONS[status];

    // then
    expect(result).toEqual(expected);
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

  it('PENDING 이어도 결제 상태가 WAITING_FOR_DEPOSIT 이 아니면 원래 라벨을 유지한다', () => {
    // given & when
    const result = getOrderDisplayStatus('PENDING', 'READY');

    // then
    expect(result).toEqual({
      label: ORDER_STATUS_LABEL.PENDING,
      variant: ORDER_STATUS_BADGE.PENDING,
    });
  });

  it('결제 상태가 없어도 원래 주문 상태 라벨을 돌려준다', () => {
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
