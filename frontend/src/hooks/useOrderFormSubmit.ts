import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type MutableRefObject } from 'react';
import { useNavigate } from 'react-router-dom';

import { useToast } from '@/components/common/toastContext';
import { usePurchaseLimitedDrop } from '@/hooks/mutations/useLimitedDropMutations';
import { useCreateOrder, useUpdateOrderShippingAddress } from '@/hooks/mutations/useOrderMutations';
import { addressKeys, couponKeys, limitedDropKeys, orderKeys } from '@/hooks/queries/queryKeys';
import { usePaymentWindow } from '@/hooks/usePaymentWindow';
import { useAuthStore } from '@/store/authStore';
import type { AvailableCoupon } from '@/types/coupon';
import type { PaymentMethodOption } from '@/types/payment';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';
import { buildLimitedPurchaseResultState, classifyPurchaseError } from '@/utils/limitedDrop';
import {
  buildOrderFingerprint,
  saveOrderFormDraft,
  toOrderCreateRequest,
  type OrderDraft,
  type PendingOrder,
  type PurchasableOrderDraft,
} from '@/utils/orderDraft';
import { getServerNowMs, toServerMs } from '@/utils/serverTime';

const STOCK_ERROR_CODES = new Set(['STOCK_INSUFFICIENT', 'STOCK_CONFLICT']);
const IDEMPOTENCY_KEY_REUSED = 'IDEMPOTENCY_KEY_REUSED';
const COUPON_ERROR_CODES = new Set([
  'COUPON_NOT_FOUND',
  'COUPON_EXPIRED',
  'COUPON_DISABLED',
  'COUPON_ALREADY_USED',
  'COUPON_MIN_ORDER_AMOUNT_NOT_MET',
]);
const ORDER_NOT_FOUND = 'ORDER_NOT_FOUND';
const MEMBER_ADDRESS_NOT_FOUND = 'MEMBER_ADDRESS_NOT_FOUND';

/** 한정반은 지문 비교 대신 만료 여부만 보고 재사용하므로, 저장용으로만 쓰는 표식이다. */
const LIMITED_FINGERPRINT = 'limited';

interface UseOrderFormSubmitParams {
  draft: OrderDraft | null;
  addressId: number | undefined;
  /** 현재 선택된 쿠폰. 만든 PENDING 주문에 그대로 스냅샷으로 남긴다. */
  coupon: AvailableCoupon | null;
  /** 결제창에 넘길 주문명("생수 외 1건"). buildOrderName 으로 미리 만들어 넘긴다. */
  orderName: string;
  onCouponRejected: () => void;
  /** sessionStorage 초안에서 복원한 PENDING 주문. 지문이 같으면 그대로 재사용한다. */
  initialPendingOrder?: PendingOrder | null;
}

interface UseOrderFormSubmitResult {
  submit: (method: PaymentMethodOption) => void;
  isSubmitting: boolean;
  /** 이 주문서에서 만든(또는 초안에서 복원한) PENDING 주문. */
  pendingOrder: PendingOrder | null;
  /**
   * 현재 입력(상품+쿠폰 지문)으로 제출하면 재사용될 PENDING 주문. 없으면 새 주문을 만든다.
   * 렌더를 순수하게 두려고 만료는 보지 않는다 - 만료는 제출 시점에 다시 확인하고, 만료됐으면
   * 같은 입력으로 새로 만든다. 화면의 최종 금액을 결제창 금액과 맞추는 데 쓴다.
   */
  reusableOrder: PendingOrder | null;
  /** 제출이 확정돼 이동하는 중임을 표시한다. useBlocker 가 이탈 확인창을 띄우지 않게 참조한다. */
  submittedRef: MutableRefObject<boolean>;
}

/**
 * 주문서 제출을 draft 종류별로 나눠 처리한다. cart/direct 는 POST /orders 로,
 * limited 는 한정반 선착순 구매 API 로 확정한 뒤 곧바로 결제창을 연다.
 *
 * 만든 PENDING 주문은 상품(수량 포함)+쿠폰 지문이 같고 만료 전이면 그대로 재사용한다 - 배송지만
 * 바뀌었으면 PATCH 로 고친 뒤 같은 주문으로 결제창을 연다. 지문이 다르거나 만료면
 * POST /orders 로 새로 만든다(서버가 이전 PENDING 을 SUPERSEDED 로 해제한다). 한정반은
 * 재구매(ALREADY_PURCHASED)가 막혀 있어 지문 비교 없이 만료 전이면 같은 주문을 재사용하고,
 * 만료됐으면 선착순 구매를 다시 시도한다.
 */
export function useOrderFormSubmit({
  draft,
  addressId,
  coupon,
  orderName,
  onCouponRejected,
  initialPendingOrder = null,
}: UseOrderFormSubmitParams): UseOrderFormSubmitResult {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const member = useAuthStore((s) => s.member);
  const submittedRef = useRef(false);
  const [pendingOrder, setPendingOrder] = useState<PendingOrder | null>(initialPendingOrder);
  const memberCouponId = coupon?.memberCouponId ?? null;
  const { openPaymentWindow, isOpening } = usePaymentWindow();

  const createOrderMutation = useCreateOrder();
  const updateShippingAddressMutation = useUpdateOrderShippingAddress();
  const purchaseLimitedMutation = usePurchaseLimitedDrop(
    draft?.kind === 'limited' ? draft.dropId : 0,
  );

  /** 결제창을 열기 직전에 이번 시도를 sessionStorage 에 남긴다(결제 실패 시 주문서 복원용). */
  const openForOrder = (source: OrderDraft, order: PendingOrder, method: PaymentMethodOption) => {
    saveOrderFormDraft({
      source,
      addressId: order.addressId,
      memberCouponId: order.coupon?.memberCouponId ?? null,
      method,
      pendingOrder: order,
    });
    return openPaymentWindow({
      orderId: order.orderId,
      orderNumber: order.orderNumber,
      orderName,
      amount: order.amount,
      method,
      customerEmail: member?.email,
    });
  };

  /** 이미 만든 주문을 재사용한다. 배송지가 그때와 다르면 먼저 PATCH 로 고친다. */
  const reuseWithAddress = (
    source: OrderDraft,
    order: PendingOrder,
    method: PaymentMethodOption,
    nextAddressId: number,
  ) => {
    if (nextAddressId === order.addressId) {
      void openForOrder(source, order, method);
      return;
    }
    updateShippingAddressMutation.mutate(
      { orderId: order.orderId, addressId: nextAddressId },
      {
        onSuccess: () => {
          const updated: PendingOrder = { ...order, addressId: nextAddressId };
          setPendingOrder(updated);
          void openForOrder(source, updated, method);
        },
        onError: (error) => {
          const code = getErrorCode(error);
          if (code === MEMBER_ADDRESS_NOT_FOUND) {
            queryClient.invalidateQueries({ queryKey: addressKeys.all });
          }
          if (code === ORDER_NOT_FOUND) {
            // 이 주문이 이미 사라졌다(다른 곳에서 해제·만료됨) - 다음 제출이 새 주문을 만들게 한다.
            setPendingOrder(null);
          }
          showToast('error', getErrorMessage(error));
        },
      },
    );
  };

  const submitLimitedPurchase = (dropId: number, method: PaymentMethodOption) => {
    if (addressId === undefined) {
      return;
    }
    purchaseLimitedMutation.mutate(
      { addressId },
      {
        onSuccess: (data) => {
          const order: PendingOrder = {
            orderId: data.orderId,
            orderNumber: data.orderNumber,
            amount: data.finalAmount,
            fingerprint: LIMITED_FINGERPRINT,
            addressId,
            expiresAtMs: toServerMs(data.expiresAt),
            coupon: null,
          };
          setPendingOrder(order);
          void openForOrder({ kind: 'limited', dropId }, order, method);
        },
        onError: (error) => {
          const kind = classifyPurchaseError(getErrorCode(error));
          if (kind === 'SOLD_OUT' || kind === 'ALREADY_PURCHASED') {
            submittedRef.current = true;
            queryClient.invalidateQueries({ queryKey: limitedDropKeys.detail(dropId) });
            navigate(`/limited-drops/${dropId}`, {
              replace: true,
              state: buildLimitedPurchaseResultState(kind),
            });
            return;
          }
          if (kind === 'STATE_CHANGED') {
            submittedRef.current = true;
            queryClient.invalidateQueries({ queryKey: limitedDropKeys.detail(dropId) });
            showToast('error', getErrorMessage(error));
            navigate(`/limited-drops/${dropId}`, { replace: true });
            return;
          }
          if (kind === 'ADDRESS_MISSING') {
            queryClient.invalidateQueries({ queryKey: addressKeys.all });
            showToast('error', getErrorMessage(error));
            return;
          }
          showToast('error', getErrorMessage(error));
        },
      },
    );
  };

  const submitCartOrDirectOrder = (
    payloadDraft: PurchasableOrderDraft,
    method: PaymentMethodOption,
  ) => {
    if (addressId === undefined) {
      return;
    }
    createOrderMutation.mutate(
      {
        payload: toOrderCreateRequest(payloadDraft, addressId, memberCouponId),
        idempotencyKey: crypto.randomUUID(),
      },
      {
        onSuccess: (data) => {
          const order: PendingOrder = {
            orderId: data.orderId,
            orderNumber: data.orderNumber,
            amount: data.finalAmount,
            fingerprint: buildOrderFingerprint(payloadDraft, memberCouponId),
            addressId,
            expiresAtMs: data.expiresAt ? toServerMs(data.expiresAt) : null,
            coupon,
          };
          setPendingOrder(order);
          void openForOrder(payloadDraft, order, method);
        },
        onError: (error) => {
          const code = getErrorCode(error);
          if (code === IDEMPOTENCY_KEY_REUSED) {
            submittedRef.current = true;
            queryClient.invalidateQueries({ queryKey: orderKeys.all });
            showToast('error', getErrorMessage(error));
            navigate('/orders', { replace: true });
            return;
          }
          if (code && STOCK_ERROR_CODES.has(code)) {
            submittedRef.current = true;
            showToast('error', getErrorMessage(error));
            navigate('/cart', { replace: true });
            return;
          }
          if (code === MEMBER_ADDRESS_NOT_FOUND) {
            queryClient.invalidateQueries({ queryKey: addressKeys.all });
            showToast('error', getErrorMessage(error));
            return;
          }
          if (code && COUPON_ERROR_CODES.has(code)) {
            onCouponRejected();
            queryClient.invalidateQueries({ queryKey: couponKeys.all });
            showToast('error', `${getErrorMessage(error)} 쿠폰을 해제했으니 다시 확인해주세요.`);
            return;
          }
          showToast('error', getErrorMessage(error));
        },
      },
    );
  };

  const submit = (method: PaymentMethodOption) => {
    if (draft === null || addressId === undefined) {
      return;
    }

    if (pendingOrder) {
      if (draft.kind === 'limited') {
        const expired =
          pendingOrder.expiresAtMs !== null && getServerNowMs() >= pendingOrder.expiresAtMs;
        if (!expired) {
          reuseWithAddress(draft, pendingOrder, method, addressId);
          return;
        }
        // 만료돼 서버가 이미 해제했다 - 선착순 구매를 다시 시도한다.
        setPendingOrder(null);
      } else {
        const fingerprint = buildOrderFingerprint(draft, memberCouponId);
        const notExpired =
          pendingOrder.expiresAtMs === null || getServerNowMs() < pendingOrder.expiresAtMs;
        if (pendingOrder.fingerprint === fingerprint && notExpired) {
          reuseWithAddress(draft, pendingOrder, method, addressId);
          return;
        }
        // 지문이 바뀌었거나 만료됐다 - POST /orders 가 이전 PENDING 을 SUPERSEDED 로 풀고 새로 만든다.
      }
    }

    if (draft.kind === 'limited') {
      submitLimitedPurchase(draft.dropId, method);
      return;
    }
    submitCartOrDirectOrder(draft, method);
  };

  const isSubmitting =
    (draft?.kind === 'limited'
      ? purchaseLimitedMutation.isPending
      : createOrderMutation.isPending) ||
    updateShippingAddressMutation.isPending ||
    isOpening;

  const reusableOrder =
    pendingOrder === null || draft === null
      ? null
      : draft.kind === 'limited'
        ? pendingOrder
        : pendingOrder.fingerprint === buildOrderFingerprint(draft, memberCouponId)
          ? pendingOrder
          : null;

  return { submit, isSubmitting, pendingOrder, reusableOrder, submittedRef };
}
