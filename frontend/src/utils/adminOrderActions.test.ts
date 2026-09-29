import { describe, expect, it } from 'vitest';

import type { AdminOrderItemSummary } from '@/types/adminOrder';
import {
  canCancelItem,
  canConfirmItem,
  canDeliverItem,
  canShipItem,
  getClaimActions,
} from '@/utils/adminOrderActions';

const item = (overrides: Partial<AdminOrderItemSummary>): AdminOrderItemSummary => ({
  id: 1,
  orderId: 1,
  productOrderNumber: 'ORD-1-01',
  orderNumber: 'ORD-1',
  memberEmail: 'member@groove.com',
  productName: '레코드',
  quantity: 1,
  status: 'PAID',
  createdAt: '2026-09-13T00:00:00',
  ...overrides,
});

describe('getClaimActions()', () => {
  it.each([
    ['CANCEL', 'REQUESTED', ['approve', 'reject']],
    ['RETURN', 'REQUESTED', ['collect', 'reject']],
    ['RETURN', 'COLLECTING', ['complete', 'reject']],
    ['CANCEL', 'DONE', []],
    ['RETURN', 'REJECTED', []],
    ['RETURN', 'WITHDRAWN', []],
  ] as const)('%s 클레임이 %s 이면 %j 만 허용한다', (type, status, expected) => {
    // when & then
    expect(getClaimActions({ type, status })).toEqual(expected);
  });
});

describe('상품주문 이행 가능 여부', () => {
  it('상태별로 다음 단계 처리만 허용한다', () => {
    // when & then
    expect(canConfirmItem(item({ status: 'PAID' }))).toBe(true);
    expect(canConfirmItem(item({ status: 'PREPARING' }))).toBe(false);
    expect(canShipItem(item({ status: 'PREPARING' }))).toBe(true);
    expect(canShipItem(item({ status: 'PAID' }))).toBe(false);
    expect(canDeliverItem(item({ status: 'SHIPPING' }))).toBe(true);
    expect(canDeliverItem(item({ status: 'DELIVERED' }))).toBe(false);
    expect(canCancelItem(item({ status: 'PREPARING' }))).toBe(true);
    expect(canCancelItem(item({ status: 'SHIPPING' }))).toBe(false);
  });

  it('진행 중인 클레임이 있으면 어떤 처리도 허용하지 않는다', () => {
    // given
    const claimed = item({ status: 'PAID', claimStatus: 'CANCEL_REQUEST' });

    // when & then
    expect(canConfirmItem(claimed)).toBe(false);
    expect(canCancelItem(claimed)).toBe(false);
  });

  it('끝난 클레임(거부)은 진행 중으로 보지 않는다', () => {
    // when & then
    expect(canConfirmItem(item({ status: 'PAID', claimStatus: 'CANCEL_REJECT' }))).toBe(true);
  });
});
