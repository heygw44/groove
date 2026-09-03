import type { OrderCreateRequest } from '@/types/order';

export type OrderDraft =
  { kind: 'cart'; cartItemIds: number[] } | { kind: 'direct'; productId: number; quantity: number };

const isPositiveInteger = (value: unknown): value is number =>
  typeof value === 'number' && Number.isInteger(value) && value > 0;

/** 장바구니/상품 상세에서 navigate 로 넘긴 location.state 를 검증한다. */
export const parseOrderDraft = (state: unknown): OrderDraft | null => {
  if (typeof state !== 'object' || state === null) {
    return null;
  }

  if ('cartItemIds' in state) {
    const { cartItemIds } = state as { cartItemIds: unknown };
    if (
      Array.isArray(cartItemIds) &&
      cartItemIds.length > 0 &&
      cartItemIds.every(isPositiveInteger)
    ) {
      return { kind: 'cart', cartItemIds };
    }
    return null;
  }

  if ('productId' in state && 'quantity' in state) {
    const { productId, quantity } = state as { productId: unknown; quantity: unknown };
    if (isPositiveInteger(productId) && isPositiveInteger(quantity)) {
      return { kind: 'direct', productId, quantity };
    }
    return null;
  }

  return null;
};

export const toOrderCreateRequest = (draft: OrderDraft, addressId: number): OrderCreateRequest =>
  draft.kind === 'cart'
    ? { cartItemIds: draft.cartItemIds, addressId, memberCouponId: null }
    : { productId: draft.productId, quantity: draft.quantity, addressId, memberCouponId: null };
