import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import {
  buildOrderFingerprint,
  clearOrderFormDraft,
  isSameOrderDraftSource,
  loadOrderFormDraft,
  orderDraftToLocationState,
  parseOrderDraft,
  saveOrderFormDraft,
  toOrderCreateRequest,
  type OrderFormDraftRecord,
  type PurchasableOrderDraft,
} from '@/utils/orderDraft';

describe('parseOrderDraft()', () => {
  it('cartItemId·quantity 가 양의 정수인 cartItems 는 장바구니 draft 로 판단한다', () => {
    // given
    const state = {
      cartItems: [
        { cartItemId: 1, quantity: 2 },
        { cartItemId: 3, quantity: 1 },
      ],
    };

    // when
    const result = parseOrderDraft(state);

    // then
    expect(result).toEqual({
      kind: 'cart',
      items: [
        { cartItemId: 1, quantity: 2 },
        { cartItemId: 3, quantity: 1 },
      ],
    });
  });

  it.each([
    { cartItems: [] },
    { cartItems: [{ cartItemId: 1, quantity: 0 }] },
    { cartItems: [{ cartItemId: -1, quantity: 1 }] },
    { cartItems: [{ cartItemId: 1 }] },
    { cartItems: [1, 2] },
    {
      cartItems: [
        { cartItemId: 1, quantity: 1 },
        { cartItemId: 1, quantity: 2 },
      ],
    },
    { cartItemIds: [1, 2] },
  ])('유효하지 않은 장바구니 state(%o)는 무효로 판단한다', (state) => {
    // given & when
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
    const draft: PurchasableOrderDraft = {
      kind: 'cart',
      items: [
        { cartItemId: 1, quantity: 2 },
        { cartItemId: 2, quantity: 1 },
      ],
    };

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
    const draft: PurchasableOrderDraft = {
      kind: 'cart',
      items: [
        { cartItemId: 1, quantity: 1 },
        { cartItemId: 2, quantity: 1 },
      ],
    };

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

describe('orderDraftToLocationState()', () => {
  it('parseOrderDraft 의 결과를 원래 location.state 모양으로 되돌린다', () => {
    // given & when & then
    expect(
      orderDraftToLocationState({ kind: 'cart', items: [{ cartItemId: 1, quantity: 2 }] }),
    ).toEqual({ cartItems: [{ cartItemId: 1, quantity: 2 }] });
    expect(orderDraftToLocationState({ kind: 'direct', productId: 10, quantity: 2 })).toEqual({
      productId: 10,
      quantity: 2,
    });
    expect(orderDraftToLocationState({ kind: 'limited', dropId: 7 })).toEqual({ dropId: 7 });
  });

  it('장바구니 draft 는 parseOrderDraft 를 거쳐 같은 draft 로 돌아온다', () => {
    // given
    const draft = {
      kind: 'cart' as const,
      items: [
        { cartItemId: 1, quantity: 2 },
        { cartItemId: 4, quantity: 3 },
      ],
    };

    // when
    const result = parseOrderDraft(orderDraftToLocationState(draft));

    // then
    expect(result).toEqual(draft);
  });
});

describe('isSameOrderDraftSource()', () => {
  it('cartItemId·quantity 구성이 같으면(순서 달라도) 같은 draft 로 본다', () => {
    // given & when & then
    expect(
      isSameOrderDraftSource(
        {
          kind: 'cart',
          items: [
            { cartItemId: 1, quantity: 2 },
            { cartItemId: 2, quantity: 1 },
          ],
        },
        {
          kind: 'cart',
          items: [
            { cartItemId: 2, quantity: 1 },
            { cartItemId: 1, quantity: 2 },
          ],
        },
      ),
    ).toBe(true);
  });

  it('cartItemId 구성이 다르면 다른 draft 로 본다', () => {
    // given & when & then
    expect(
      isSameOrderDraftSource(
        { kind: 'cart', items: [{ cartItemId: 1, quantity: 1 }] },
        { kind: 'cart', items: [{ cartItemId: 3, quantity: 1 }] },
      ),
    ).toBe(false);
  });

  it('장바구니 항목의 수량만 달라도 다른 draft 로 본다', () => {
    // given & when & then
    expect(
      isSameOrderDraftSource(
        { kind: 'cart', items: [{ cartItemId: 1, quantity: 1 }] },
        { kind: 'cart', items: [{ cartItemId: 1, quantity: 2 }] },
      ),
    ).toBe(false);
  });

  it('kind 가 다르면 다른 draft 로 본다', () => {
    // given & when & then
    expect(
      isSameOrderDraftSource(
        { kind: 'cart', items: [{ cartItemId: 1, quantity: 1 }] },
        { kind: 'direct', productId: 1, quantity: 1 },
      ),
    ).toBe(false);
  });

  it('direct 는 productId·quantity 가 같아야 같은 draft 로 본다', () => {
    // given & when & then
    expect(
      isSameOrderDraftSource(
        { kind: 'direct', productId: 10, quantity: 2 },
        { kind: 'direct', productId: 10, quantity: 2 },
      ),
    ).toBe(true);
    expect(
      isSameOrderDraftSource(
        { kind: 'direct', productId: 10, quantity: 2 },
        { kind: 'direct', productId: 10, quantity: 3 },
      ),
    ).toBe(false);
  });

  it('limited 는 dropId 가 같아야 같은 draft 로 본다', () => {
    // given & when & then
    expect(
      isSameOrderDraftSource({ kind: 'limited', dropId: 7 }, { kind: 'limited', dropId: 7 }),
    ).toBe(true);
    expect(
      isSameOrderDraftSource({ kind: 'limited', dropId: 7 }, { kind: 'limited', dropId: 8 }),
    ).toBe(false);
  });
});

describe('buildOrderFingerprint()', () => {
  it('장바구니 항목 순서가 달라도 같은 지문을 만든다', () => {
    // given & when
    const a = buildOrderFingerprint(
      {
        kind: 'cart',
        items: [
          { cartItemId: 1, quantity: 2 },
          { cartItemId: 2, quantity: 1 },
        ],
      },
      null,
    );
    const b = buildOrderFingerprint(
      {
        kind: 'cart',
        items: [
          { cartItemId: 2, quantity: 1 },
          { cartItemId: 1, quantity: 2 },
        ],
      },
      null,
    );

    // then
    expect(a).toBe(b);
  });

  it('장바구니 항목의 수량이 다르면 다른 지문을 만든다', () => {
    // given & when
    const a = buildOrderFingerprint(
      { kind: 'cart', items: [{ cartItemId: 1, quantity: 1 }] },
      null,
    );
    const b = buildOrderFingerprint(
      { kind: 'cart', items: [{ cartItemId: 1, quantity: 2 }] },
      null,
    );

    // then
    expect(a).not.toBe(b);
  });

  it('쿠폰이 다르면 다른 지문을 만든다', () => {
    // given & when
    const a = buildOrderFingerprint(
      { kind: 'cart', items: [{ cartItemId: 1, quantity: 1 }] },
      null,
    );
    const b = buildOrderFingerprint({ kind: 'cart', items: [{ cartItemId: 1, quantity: 1 }] }, 7);

    // then
    expect(a).not.toBe(b);
  });

  it('직접 구매는 productId·quantity·쿠폰이 모두 같아야 같은 지문이다', () => {
    // given & when
    const a = buildOrderFingerprint({ kind: 'direct', productId: 1, quantity: 2 }, 3);
    const b = buildOrderFingerprint({ kind: 'direct', productId: 1, quantity: 2 }, 3);
    const c = buildOrderFingerprint({ kind: 'direct', productId: 1, quantity: 5 }, 3);

    // then
    expect(a).toBe(b);
    expect(a).not.toBe(c);
  });
});

describe('sessionStorage 주문서 초안', () => {
  const buildRecord = (overrides: Partial<OrderFormDraftRecord> = {}): OrderFormDraftRecord => ({
    source: { kind: 'cart', items: [{ cartItemId: 1, quantity: 1 }] },
    addressId: 5,
    memberCouponId: null,
    method: 'CARD',
    pendingOrder: {
      orderId: 1,
      orderNumber: 'ORD-1',
      amount: 10000,
      fingerprint: 'fp',
      addressId: 5,
      expiresAtMs: null,
      coupon: null,
    },
    ...overrides,
  });

  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('저장한 초안을 그대로 읽어온다', () => {
    // given
    const record = buildRecord();

    // when
    saveOrderFormDraft(record);
    const result = loadOrderFormDraft();

    // then
    expect(result).toEqual(record);
  });

  it('저장된 게 없으면 null 을 반환한다', () => {
    // given & when
    const result = loadOrderFormDraft();

    // then
    expect(result).toBeNull();
  });

  it('스키마가 깨진 값은 무시하고 null 을 반환한다', () => {
    // given
    sessionStorage.setItem('groove:orderFormDraft', JSON.stringify({ foo: 'bar' }));

    // when
    const result = loadOrderFormDraft();

    // then
    expect(result).toBeNull();
  });

  it('JSON 이 아닌 값도 무시하고 null 을 반환한다', () => {
    // given
    sessionStorage.setItem('groove:orderFormDraft', '{not json');

    // when
    const result = loadOrderFormDraft();

    // then
    expect(result).toBeNull();
  });

  it('pendingOrder 가 없는 초안도 유효하게 읽는다', () => {
    // given
    const record = buildRecord({ pendingOrder: null });

    // when
    saveOrderFormDraft(record);
    const result = loadOrderFormDraft();

    // then
    expect(result).toEqual(record);
  });

  it('pendingOrder 에 건 쿠폰 스냅샷도 그대로 읽어온다', () => {
    // given
    const record = buildRecord({
      memberCouponId: 9,
      pendingOrder: {
        orderId: 1,
        orderNumber: 'ORD-1',
        amount: 9000,
        fingerprint: 'fp',
        addressId: 5,
        expiresAtMs: null,
        coupon: {
          memberCouponId: 9,
          couponCode: 'WELCOME',
          couponName: '웰컴 쿠폰',
          expiresAt: '2026-12-31T23:59:59',
          expectedDiscount: 1000,
          discountType: 'FIXED',
          discountValue: 1000,
          minOrderAmount: 0,
        },
      },
    });

    // when
    saveOrderFormDraft(record);
    const result = loadOrderFormDraft();

    // then
    expect(result).toEqual(record);
  });

  it('pendingOrder 의 쿠폰 스냅샷이 깨졌으면 null 을 반환한다', () => {
    // given
    const record = buildRecord();
    sessionStorage.setItem(
      'groove:orderFormDraft',
      JSON.stringify({
        ...record,
        pendingOrder: { ...record.pendingOrder, coupon: { memberCouponId: 9 } },
      }),
    );

    // when
    const result = loadOrderFormDraft();

    // then
    expect(result).toBeNull();
  });

  it('예전 cartItemIds 모양으로 저장된 초안은 null 을 반환한다', () => {
    // given
    sessionStorage.setItem(
      'groove:orderFormDraft',
      JSON.stringify({ ...buildRecord(), source: { kind: 'cart', cartItemIds: [1] } }),
    );

    // when
    const result = loadOrderFormDraft();

    // then
    expect(result).toBeNull();
  });

  it('clearOrderFormDraft 는 저장된 초안을 지운다', () => {
    // given
    saveOrderFormDraft(buildRecord());

    // when
    clearOrderFormDraft();

    // then
    expect(loadOrderFormDraft()).toBeNull();
  });
});
