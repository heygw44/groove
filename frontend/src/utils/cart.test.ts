import { describe, expect, it } from 'vitest';

import type { CartItem } from '@/types/cart';
import { isCartItemSoldOut, maxSelectableQuantity, sumSelectedSubtotal } from '@/utils/cart';

const createCartItem = (overrides: Partial<CartItem> = {}): CartItem => ({
  id: 1,
  productId: 1,
  title: '테스트 앨범',
  artistName: '테스트 아티스트',
  price: 10000,
  productStatus: 'ON_SALE',
  stockQuantity: 5,
  quantity: 1,
  subtotal: 10000,
  ...overrides,
});

describe('isCartItemSoldOut()', () => {
  it('상품 상태가 SOLD_OUT 이면 품절로 판단한다', () => {
    // given
    const item = createCartItem({ productStatus: 'SOLD_OUT' });

    // when
    const result = isCartItemSoldOut(item);

    // then
    expect(result).toBe(true);
  });

  it('재고 수량이 0 이하이면 품절로 판단한다', () => {
    // given
    const item = createCartItem({ stockQuantity: 0 });

    // when
    const result = isCartItemSoldOut(item);

    // then
    expect(result).toBe(true);
  });

  it('판매중이고 재고가 있으면 품절이 아니다', () => {
    // given
    const item = createCartItem({ productStatus: 'ON_SALE', stockQuantity: 3 });

    // when
    const result = isCartItemSoldOut(item);

    // then
    expect(result).toBe(false);
  });
});

describe('sumSelectedSubtotal()', () => {
  it('선택된 항목의 소계만 더한다', () => {
    // given
    const items = [
      createCartItem({ id: 1, subtotal: 10000 }),
      createCartItem({ id: 2, subtotal: 20000 }),
      createCartItem({ id: 3, subtotal: 30000 }),
    ];

    // when
    const result = sumSelectedSubtotal(items, new Set([1, 3]));

    // then
    expect(result).toBe(40000);
  });

  it('선택된 항목이 없으면 0을 반환한다', () => {
    // given
    const items = [createCartItem({ id: 1, subtotal: 10000 })];

    // when
    const result = sumSelectedSubtotal(items, new Set());

    // then
    expect(result).toBe(0);
  });
});

describe('maxSelectableQuantity()', () => {
  it('재고가 최대 수량보다 적으면 재고 수량을 반환한다', () => {
    // given
    const item = createCartItem({ stockQuantity: 3 });

    // when
    const result = maxSelectableQuantity(item);

    // then
    expect(result).toBe(3);
  });

  it('재고가 최대 수량보다 많으면 최대 수량을 반환한다', () => {
    // given
    const item = createCartItem({ stockQuantity: 50 });

    // when
    const result = maxSelectableQuantity(item);

    // then
    expect(result).toBe(10);
  });
});
