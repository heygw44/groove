import { describe, expect, it } from 'vitest';

import {
  parseOrderDraft,
  toOrderCreateRequest,
  type PurchasableOrderDraft,
} from '@/utils/orderDraft';

describe('parseOrderDraft()', () => {
  it('양의 정수 배열인 cartItemIds 는 장바구니 draft 로 판단한다', () => {
    // given
    const state = { cartItemIds: [1, 2, 3] };

    // when
    const result = parseOrderDraft(state);

    // then
    expect(result).toEqual({ kind: 'cart', cartItemIds: [1, 2, 3] });
  });

  it('빈 cartItemIds 배열은 무효로 판단한다', () => {
    // given
    const state = { cartItemIds: [] };

    // when
    const result = parseOrderDraft(state);

    // then
    expect(result).toBeNull();
  });

  it('cartItemIds 에 양의 정수가 아닌 값이 있으면 무효로 판단한다', () => {
    // given
    const state = { cartItemIds: [1, -2] };

    // when
    const result = parseOrderDraft(state);

    // then
    expect(result).toBeNull();
  });

  it('양의 정수 productId·quantity 는 직접 구매 draft 로 판단한다', () => {
    // given
    const state = { productId: 10, quantity: 2 };

    // when
    const result = parseOrderDraft(state);

    // then
    expect(result).toEqual({ kind: 'direct', productId: 10, quantity: 2 });
  });

  it('quantity 가 0 이하이면 무효로 판단한다', () => {
    // given
    const state = { productId: 10, quantity: 0 };

    // when
    const result = parseOrderDraft(state);

    // then
    expect(result).toBeNull();
  });

  it('null 이나 알 수 없는 모양의 state 는 무효로 판단한다', () => {
    // given & when & then
    expect(parseOrderDraft(null)).toBeNull();
    expect(parseOrderDraft(undefined)).toBeNull();
    expect(parseOrderDraft({})).toBeNull();
    expect(parseOrderDraft('cartItemIds')).toBeNull();
  });

  it('양의 정수 dropId 는 한정반 draft 로 판단한다', () => {
    // given
    const state = { dropId: 7 };

    // when
    const result = parseOrderDraft(state);

    // then
    expect(result).toEqual({ kind: 'limited', dropId: 7 });
  });

  it.each([{ dropId: 0 }, { dropId: -1 }, { dropId: '7' }])(
    '유효하지 않은 dropId(%o)는 무효로 판단한다',
    (state) => {
      // when
      const result = parseOrderDraft(state);

      // then
      expect(result).toBeNull();
    },
  );
});

describe('toOrderCreateRequest()', () => {
  it('장바구니 draft 는 cartItemIds 와 addressId 를 담는다', () => {
    // given
    const draft: PurchasableOrderDraft = { kind: 'cart', cartItemIds: [1, 2] };

    // when
    const result = toOrderCreateRequest(draft, 5);

    // then
    expect(result).toEqual({ cartItemIds: [1, 2], addressId: 5, memberCouponId: null });
  });

  it('직접 구매 draft 는 productId 와 quantity 를 담는다', () => {
    // given
    const draft: PurchasableOrderDraft = { kind: 'direct', productId: 10, quantity: 3 };

    // when
    const result = toOrderCreateRequest(draft, 5);

    // then
    expect(result).toEqual({ productId: 10, quantity: 3, addressId: 5, memberCouponId: null });
  });

  it('장바구니 draft 에 memberCouponId 를 넘기면 그대로 담긴다', () => {
    // given
    const draft: PurchasableOrderDraft = { kind: 'cart', cartItemIds: [1, 2] };

    // when
    const result = toOrderCreateRequest(draft, 5, 7);

    // then
    expect(result).toEqual({ cartItemIds: [1, 2], addressId: 5, memberCouponId: 7 });
  });

  it('직접 구매 draft 에 memberCouponId 를 넘기면 그대로 담긴다', () => {
    // given
    const draft: PurchasableOrderDraft = { kind: 'direct', productId: 10, quantity: 3 };

    // when
    const result = toOrderCreateRequest(draft, 5, 7);

    // then
    expect(result).toEqual({ productId: 10, quantity: 3, addressId: 5, memberCouponId: 7 });
  });
});
