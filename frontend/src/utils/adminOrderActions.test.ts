import { describe, expect, it } from 'vitest';

import type { AdminOrderItemSummary } from '@/types/adminOrder';
import {
  canCancelItem,
  canConfirmItem,
  canDeliverItem,
  canShipItem,
  CLAIM_STATUSES_BY_TYPE,
  getClaimActions,
  getClaimStatusLabel,
  pickClaimCount,
  countActionableClaims,
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

describe('getClaimStatusLabel()', () => {
  it.each([
    ['CANCEL', 'REQUESTED', '취소요청'],
    ['CANCEL', 'DONE', '취소완료'],
    ['CANCEL', 'REJECTED', '취소거부'],
    ['CANCEL', 'WITHDRAWN', '요청철회'],
    ['RETURN', 'REQUESTED', '반품요청'],
    ['RETURN', 'COLLECTING', '수거중'],
    ['RETURN', 'DONE', '반품완료'],
    ['RETURN', 'REJECTED', '반품거부'],
    ['RETURN', 'WITHDRAWN', '요청철회'],
  ] as const)('%s 클레임의 %s 상태는 %s 로 표기한다', (type, status, expected) => {
    // when & then
    expect(getClaimStatusLabel(type, status)).toBe(expected);
  });
});

describe('CLAIM_STATUSES_BY_TYPE', () => {
  it('취소에는 수거중이 없고 반품에는 있다', () => {
    // when & then
    expect(CLAIM_STATUSES_BY_TYPE.CANCEL).not.toContain('COLLECTING');
    expect(CLAIM_STATUSES_BY_TYPE.RETURN).toContain('COLLECTING');
  });
});

describe('pickClaimCount()', () => {
  it('상태에 해당하는 건수 필드를 돌려준다', () => {
    // given
    const counts = { requested: 1, collecting: 2, done: 3, rejected: 4, withdrawn: 5, total: 15 };

    // when & then
    expect(pickClaimCount(counts, 'COLLECTING')).toBe(2);
    expect(pickClaimCount(counts, 'WITHDRAWN')).toBe(5);
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

describe('countActionableClaims()', () => {
  const counts = { requested: 2, collecting: 3, done: 4, rejected: 0, withdrawn: 1, total: 10 };

  it('취소는 취소요청 건수만 센다', () => {
    // when & then
    expect(countActionableClaims(counts, 'CANCEL')).toBe(2);
  });

  it('반품은 반품요청과 수거중을 더해 센다', () => {
    // when & then
    expect(countActionableClaims(counts, 'RETURN')).toBe(5);
  });
});
