import type { OrderCreateRequest } from '@/types/order';
import type { PaymentMethodOption } from '@/types/payment';

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

/** parseOrderDraft 의 역변환. 주문서를 다시 열 때 navigate 의 location.state 로 그대로 넘긴다. */
export const orderDraftToLocationState = (draft: OrderDraft): Record<string, unknown> => {
  if (draft.kind === 'cart') {
    return { cartItemIds: draft.cartItemIds };
  }
  if (draft.kind === 'direct') {
    return { productId: draft.productId, quantity: draft.quantity };
  }
  return { dropId: draft.dropId };
};

/** 두 draft 가 같은 상품 구성을 가리키는지(수량·쿠폰은 별도 취급). */
export const isSameOrderDraftSource = (a: OrderDraft, b: OrderDraft): boolean => {
  if (a.kind !== b.kind) {
    return false;
  }
  if (a.kind === 'cart' && b.kind === 'cart') {
    const left = [...a.cartItemIds].sort((x, y) => x - y);
    const right = [...b.cartItemIds].sort((x, y) => x - y);
    return left.length === right.length && left.every((id, index) => id === right[index]);
  }
  if (a.kind === 'direct' && b.kind === 'direct') {
    return a.productId === b.productId && a.quantity === b.quantity;
  }
  return a.kind === 'limited' && b.kind === 'limited' && a.dropId === b.dropId;
};

export const toOrderCreateRequest = (
  draft: PurchasableOrderDraft,
  addressId: number,
  memberCouponId: number | null = null,
): OrderCreateRequest =>
  draft.kind === 'cart'
    ? { cartItemIds: draft.cartItemIds, addressId, memberCouponId }
    : { productId: draft.productId, quantity: draft.quantity, addressId, memberCouponId };

/**
 * "상품+쿠폰이 같으면 같은 주문" 판정에 쓰는 지문. 한정반은 재구매가 막혀 있어
 * 이 지문을 비교하지 않고 항상 같은 주문을 재사용한다(호출부에서 따로 분기).
 */
export const buildOrderFingerprint = (
  draft: PurchasableOrderDraft,
  memberCouponId: number | null,
): string =>
  draft.kind === 'cart'
    ? JSON.stringify({
        kind: draft.kind,
        cartItemIds: [...draft.cartItemIds].sort((a, b) => a - b),
        memberCouponId,
      })
    : JSON.stringify({
        kind: draft.kind,
        productId: draft.productId,
        quantity: draft.quantity,
        memberCouponId,
      });

/** 이 주문서에서 만든 PENDING 주문. 지문·만료시각이 같은 동안은 재사용한다. */
export interface PendingOrder {
  orderId: number;
  orderNumber: string;
  amount: number;
  /** 한정반이면 항상 무시하고 재사용한다(호출부에서 지문 비교를 건너뛴다). */
  fingerprint: string;
  addressId: number;
  /** 응답에 expiresAt 이 없으면 null - 만료 판정 없이 계속 재사용한다. */
  expiresAtMs: number | null;
}

export interface OrderFormDraftRecord {
  source: OrderDraft;
  addressId: number;
  memberCouponId: number | null;
  method: PaymentMethodOption;
  pendingOrder: PendingOrder | null;
}

const STORAGE_KEY = 'groove:orderFormDraft';

const PAYMENT_METHODS: readonly PaymentMethodOption[] = [
  'CARD',
  'TOSSPAY',
  'NAVERPAY',
  'KAKAOPAY',
  'VIRTUAL_ACCOUNT',
];

const isPaymentMethodOption = (value: unknown): value is PaymentMethodOption =>
  typeof value === 'string' && (PAYMENT_METHODS as readonly string[]).includes(value);

const isPositiveNumber = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value) && value > 0;

const isPendingOrder = (value: unknown): value is PendingOrder => {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const record = value as Record<string, unknown>;
  return (
    isPositiveInteger(record.orderId) &&
    typeof record.orderNumber === 'string' &&
    record.orderNumber.length > 0 &&
    isPositiveNumber(record.amount) &&
    typeof record.fingerprint === 'string' &&
    isPositiveInteger(record.addressId) &&
    (record.expiresAtMs === null || typeof record.expiresAtMs === 'number')
  );
};

const isOrderFormDraftRecord = (value: unknown): value is OrderFormDraftRecord => {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const record = value as Record<string, unknown>;
  const source = parseOrderDraft(record.source);
  if (source === null) {
    return false;
  }
  if (!isPositiveInteger(record.addressId)) {
    return false;
  }
  if (record.memberCouponId !== null && !isPositiveInteger(record.memberCouponId)) {
    return false;
  }
  if (!isPaymentMethodOption(record.method)) {
    return false;
  }
  if (record.pendingOrder !== null && !isPendingOrder(record.pendingOrder)) {
    return false;
  }
  return true;
};

/** 결제창으로 넘어가기 직전에 주문서 입력을 저장한다. 실패해도(용량 초과 등) 결제 자체는 계속 진행한다. */
export const saveOrderFormDraft = (record: OrderFormDraftRecord): void => {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(record));
  } catch {
    // 저장 실패는 조용히 무시한다 - 다음에 돌아왔을 때 복원만 안 될 뿐이다.
  }
};

/** 저장된 주문서 초안을 읽는다. 없거나 스키마가 깨졌으면 null. */
export const loadOrderFormDraft = (): OrderFormDraftRecord | null => {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed: unknown = JSON.parse(raw);
    return isOrderFormDraftRecord(parsed) ? parsed : null;
  } catch {
    return null;
  }
};

/** 결제가 확정되면 더 이상 돌아갈 실패 시도가 없으므로 지운다. */
export const clearOrderFormDraft = (): void => {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // no-op
  }
};
