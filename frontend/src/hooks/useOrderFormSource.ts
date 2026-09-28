import { useMemo } from 'react';

import type { OrderSummaryItem } from '@/components/order/OrderItemSummaryList';
import { useCart } from '@/hooks/queries/useCart';
import { useLimitedDrop } from '@/hooks/queries/useLimitedDrop';
import { useProduct } from '@/hooks/queries/useProduct';
import { useServerNow } from '@/hooks/useServerNow';
import type { LimitedDropDetail } from '@/types/limitedDrop';
import { getDropPhase, getPurchaseButtonState } from '@/utils/limitedDrop';
import type { OrderDraft } from '@/utils/orderDraft';

export interface LimitedOrderSource {
  drop: LimitedDropDetail;
  /** 지금 주문하기를 눌러도 되는 상태(OPEN 이고 아직 구매하지 않음)인지. */
  isPurchasable: boolean;
}

export interface OrderFormSource {
  items: OrderSummaryItem[];
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  retry: () => void;
  /** 한정반은 쿠폰 적용 대상이 아니다. */
  couponAllowed: boolean;
  /** true 면 이 draft 로는 주문서를 진행할 수 없어 returnTo 로 돌려보내야 한다. */
  invalid: boolean;
  returnTo: string;
  invalidMessage: string;
  limited?: LimitedOrderSource;
}

/**
 * OrderDraft 종류(cart/direct/limited)별로 다른 조회 훅을 호출해 주문서가 쓸
 * 상품 목록과 로딩/에러 상태로 좁혀준다. 각 종류의 무효 조건도 여기서 판단해
 * 페이지는 결과만 보고 리다이렉트하면 되게 한다.
 */
export function useOrderFormSource(draft: OrderDraft | null): OrderFormSource {
  const cartQuery = useCart();
  const productQuery = useProduct(draft?.kind === 'direct' ? draft.productId : 0);
  const limitedQuery = useLimitedDrop(draft?.kind === 'limited' ? draft.dropId : 0);
  const nowMs = useServerNow(1000, draft?.kind === 'limited');

  const cartItems = cartQuery.data?.items ?? [];
  const cartOrderItems =
    draft?.kind === 'cart' ? cartItems.filter((item) => draft.cartItemIds.includes(item.id)) : [];

  let items: OrderSummaryItem[] = [];
  if (draft?.kind === 'cart') {
    items = cartOrderItems.map((item) => ({
      key: item.id,
      title: item.title,
      artistName: item.artistName,
      thumbnailUrl: item.thumbnailUrl,
      price: item.price,
      quantity: item.quantity,
      lineAmount: item.subtotal,
    }));
  } else if (draft?.kind === 'direct' && productQuery.data) {
    const product = productQuery.data;
    items = [
      {
        key: product.id,
        title: product.title,
        artistName: product.artist.name,
        thumbnailUrl: product.images[0]?.url,
        price: product.price,
        quantity: draft.quantity,
        lineAmount: product.price * draft.quantity,
      },
    ];
  } else if (draft?.kind === 'limited' && limitedQuery.data) {
    const { product } = limitedQuery.data;
    items = [
      {
        key: product.id,
        title: product.title,
        artistName: product.artistName,
        thumbnailUrl: product.thumbnailUrl,
        price: product.price,
        quantity: 1,
        lineAmount: product.price,
      },
    ];
  }

  let isLoading = true;
  let isError = false;
  let error: unknown;
  let retry = () => undefined;
  if (draft?.kind === 'cart') {
    isLoading = cartQuery.isPending;
    isError = cartQuery.isError;
    error = cartQuery.error;
    retry = () => void cartQuery.refetch();
  } else if (draft?.kind === 'direct') {
    isLoading = productQuery.isPending;
    isError = productQuery.isError;
    error = productQuery.error;
    retry = () => void productQuery.refetch();
  } else if (draft?.kind === 'limited') {
    isLoading = limitedQuery.isPending;
    isError = limitedQuery.isError;
    error = limitedQuery.error;
    retry = () => void limitedQuery.refetch();
  }

  const invalid = useMemo(() => {
    if (draft === null) {
      return true;
    }
    if (draft.kind === 'cart') {
      return cartQuery.data !== undefined && cartOrderItems.length !== draft.cartItemIds.length;
    }
    if (draft.kind === 'limited') {
      // 드롭이 삭제됐거나(404) 조회 자체가 실패하면 상세로 돌아가 최신 상태를 다시 보게 한다.
      return limitedQuery.isError;
    }
    return false;
  }, [draft, cartQuery.data, cartOrderItems.length, limitedQuery.isError]);

  const returnTo = draft?.kind === 'limited' ? '/limited-drops' : '/cart';
  const invalidMessage =
    draft?.kind === 'limited'
      ? '한정반 정보를 불러오지 못했습니다.'
      : '주문할 상품을 다시 선택해주세요.';

  let limited: LimitedOrderSource | undefined;
  if (draft?.kind === 'limited' && limitedQuery.data) {
    const drop = limitedQuery.data;
    const phase = getDropPhase(drop, nowMs);
    limited = { drop, isPurchasable: !getPurchaseButtonState(drop, phase, true).disabled };
  }

  return {
    items,
    isLoading,
    isError,
    error,
    retry,
    couponAllowed: draft?.kind !== 'limited',
    invalid,
    returnTo,
    invalidMessage,
    limited,
  };
}
