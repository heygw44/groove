import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type MutableRefObject } from 'react';
import { useNavigate } from 'react-router-dom';

import { useToast } from '@/components/common/toastContext';
import { usePurchaseLimitedDrop } from '@/hooks/mutations/useLimitedDropMutations';
import { useCreateOrder } from '@/hooks/mutations/useOrderMutations';
import { addressKeys, couponKeys, limitedDropKeys, orderKeys } from '@/hooks/queries/queryKeys';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';
import { buildLimitedPurchaseResultState, classifyPurchaseError } from '@/utils/limitedDrop';
import {
  toOrderCreateRequest,
  type OrderDraft,
  type PurchasableOrderDraft,
} from '@/utils/orderDraft';

const STOCK_ERROR_CODES = new Set(['STOCK_INSUFFICIENT', 'STOCK_CONFLICT']);
const IDEMPOTENCY_KEY_REUSED = 'IDEMPOTENCY_KEY_REUSED';
const COUPON_ERROR_CODES = new Set([
  'COUPON_NOT_FOUND',
  'COUPON_EXPIRED',
  'COUPON_DISABLED',
  'COUPON_ALREADY_USED',
  'COUPON_MIN_ORDER_AMOUNT_NOT_MET',
]);

interface UseOrderFormSubmitParams {
  draft: OrderDraft | null;
  addressId: number | undefined;
  memberCouponId: number | null;
  onCouponRejected: () => void;
}

interface UseOrderFormSubmitResult {
  submit: () => void;
  isSubmitting: boolean;
  /** 제출이 확정돼 이동하는 중임을 표시한다. useBlocker 가 이탈 확인창을 띄우지 않게 참조한다. */
  submittedRef: MutableRefObject<boolean>;
}

/**
 * 주문서 제출을 draft 종류별로 나눠 처리한다. cart/direct 는 POST /orders 로,
 * limited 는 한정반 선착순 구매 API 로 확정한 뒤 결과에 따라 이동한다.
 */
export function useOrderFormSubmit({
  draft,
  addressId,
  memberCouponId,
  onCouponRejected,
}: UseOrderFormSubmitParams): UseOrderFormSubmitResult {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const submittedRef = useRef(false);

  const createOrderMutation = useCreateOrder();
  const purchaseLimitedMutation = usePurchaseLimitedDrop(
    draft?.kind === 'limited' ? draft.dropId : 0,
  );

  const submitLimitedPurchase = (dropId: number) => {
    if (addressId === undefined) {
      return;
    }
    purchaseLimitedMutation.mutate(
      { addressId },
      {
        onSuccess: (data) => {
          submittedRef.current = true;
          navigate(`/orders/${data.orderId}`, { replace: true });
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

  const submitCartOrDirectOrder = (payloadDraft: PurchasableOrderDraft) => {
    if (addressId === undefined) {
      return;
    }
    createOrderMutation.mutate(
      {
        payload: toOrderCreateRequest(payloadDraft, addressId, memberCouponId),
        idempotencyKey,
      },
      {
        onSuccess: (data) => {
          submittedRef.current = true;
          navigate(`/orders/${data.orderId}`, { replace: true });
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
          if (code === 'MEMBER_ADDRESS_NOT_FOUND') {
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

  const submit = () => {
    if (draft === null) {
      return;
    }
    if (draft.kind === 'limited') {
      submitLimitedPurchase(draft.dropId);
      return;
    }
    submitCartOrDirectOrder(draft);
  };

  const isSubmitting =
    draft?.kind === 'limited' ? purchaseLimitedMutation.isPending : createOrderMutation.isPending;

  return { submit, isSubmitting, submittedRef };
}
