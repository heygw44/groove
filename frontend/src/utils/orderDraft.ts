import type { OrderCreateRequest } from '@/types/order';

export type OrderDraft =
  | { kind: 'cart'; cartItemIds: number[] }
  | { kind: 'direct'; productId: number; quantity: number }
  | { kind: 'limited'; dropId: number };

// 한정반은 POST /orders 가 아니라 usePurchaseLimitedDrop 으로 확정하므로 요청 변환 대상에서 제외한다.
export type PurchasableOrderDraft = Exclude<OrderDraft, { kind: 'limited'; dropId: number }>;

const isPositiveInteger = (value: unknown): value is number =>
  typeof value === 'number' && Number.isInteger(value) && value > 0;

/** 장바구니/상품 상세/한정반 상세에서 navigate 로 넘긴 location.state 를 검증한다. */
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

  if ('dropId' in state) {
    const { dropId } = state as { dropId: unknown };
    if (isPositiveInteger(dropId)) {
      return { kind: 'limited', dropId };
    }
    return null;
  }

  return null;
};

export const toOrderCreateRequest = (
  draft: PurchasableOrderDraft,
  addressId: number,
  memberCouponId: number | null = null,
): OrderCreateRequest =>
  draft.kind === 'cart'
    ? { cartItemIds: draft.cartItemIds, addressId, memberCouponId }
    : { productId: draft.productId, quantity: draft.quantity, addressId, memberCouponId };
