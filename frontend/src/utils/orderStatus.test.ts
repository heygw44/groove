import { describe, expect, it } from 'vitest';

import type { OrderStatus } from '@/types/order';
import {
  ADMIN_ORDER_TRANSITIONS,
  ORDER_STATUS_LABEL,
  ORDER_STATUSES,
  isCancelableStatus,
  isOrderStatus,
} from '@/utils/orderStatus';

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
