import { describe, expect, it } from 'vitest';

import {
  ORDER_ITEM_CLAIM_STATUS_BADGE,
  ORDER_ITEM_CLAIM_STATUS_LABEL,
  ORDER_ITEM_CLAIM_STATUSES,
  ORDER_ITEM_STATUS_BADGE,
  ORDER_ITEM_STATUS_LABEL,
  ORDER_ITEM_STATUSES,
  getOrderItemDisplayStatus,
} from '@/utils/orderItemStatus';

describe('ORDER_ITEM_STATUS_LABEL', () => {
  it.each(ORDER_ITEM_STATUSES)('%s 상태의 한글 라벨을 갖는다', (status) => {
    // given & when
    const label = ORDER_ITEM_STATUS_LABEL[status];

    // then
    expect(label).toBeTruthy();
  });

  it('화면 노출 대상 목록에 내부 상태 PAYMENT_PENDING 은 없다', () => {
    // given & when & then
    expect(ORDER_ITEM_STATUSES).not.toContain('PAYMENT_PENDING');
  });
});

describe('ORDER_ITEM_CLAIM_STATUS_LABEL', () => {
  it.each(ORDER_ITEM_CLAIM_STATUSES)('%s 클레임 상태의 한글 라벨을 갖는다', (status) => {
    // given & when
    const label = ORDER_ITEM_CLAIM_STATUS_LABEL[status];

    // then
    expect(label).toBeTruthy();
  });
});

describe('getOrderItemDisplayStatus()', () => {
  it('클레임이 없으면 상품주문 상태 라벨을 보여준다', () => {
    // given & when
    const result = getOrderItemDisplayStatus('SHIPPING');

    // then
    expect(result).toEqual({
      label: ORDER_ITEM_STATUS_LABEL.SHIPPING,
      variant: ORDER_ITEM_STATUS_BADGE.SHIPPING,
    });
  });

  it.each(['CANCEL_REQUEST', 'RETURN_REQUEST', 'COLLECTING'] as const)(
    '클레임이 진행 중(%s)이면 클레임 라벨을 상품주문 상태보다 우선 보여준다',
    (claimStatus) => {
      // given & when
      const result = getOrderItemDisplayStatus('SHIPPING', claimStatus);

      // then
      expect(result).toEqual({
        label: ORDER_ITEM_CLAIM_STATUS_LABEL[claimStatus],
        variant: ORDER_ITEM_CLAIM_STATUS_BADGE[claimStatus],
      });
    },
  );

  it.each(['CANCEL_DONE', 'CANCEL_REJECT', 'RETURN_DONE', 'RETURN_REJECT'] as const)(
    '클레임이 끝났으면(%s) 상품주문 상태 라벨을 그대로 보여준다',
    (claimStatus) => {
      // given & when
      const result = getOrderItemDisplayStatus('CANCELED', claimStatus);

      // then
      expect(result).toEqual({
        label: ORDER_ITEM_STATUS_LABEL.CANCELED,
        variant: ORDER_ITEM_STATUS_BADGE.CANCELED,
      });
    },
  );
});
