import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  cancelOrder,
  cancelOrderItem,
  confirmOrderItem,
  createOrder,
  returnOrderItem,
  updateOrderShippingAddress,
  withdrawOrderClaim,
} from '@/api/order';
import {
  cartKeys,
  couponKeys,
  orderKeys,
  productKeys,
  reviewKeys,
} from '@/hooks/queries/queryKeys';
import type { OrderCreateRequest, RefundAccount } from '@/types/order';

interface CreateOrderVariables {
  payload: OrderCreateRequest;
  idempotencyKey: string;
}

export const useCreateOrder = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ payload, idempotencyKey }: CreateOrderVariables) =>
      createOrder(payload, idempotencyKey),
    // 장바구니는 주문 생성이 아니라 결제 확정 시점에 서버가 지운다(D3) - 여기서 무효화하지 않는다.
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
      // 주문에 쓴 쿠폰이 사용 완료로 바뀌므로 쿠폰함도 함께 무효화한다.
      queryClient.invalidateQueries({ queryKey: couponKeys.all });
    },
  });
};

interface UpdateOrderShippingAddressVariables {
  orderId: number;
  addressId: number;
}

/** 주문서 재제출 시 배송지만 바뀐 경우 쓴다(D2). 상품·쿠폰이 같은 PENDING 주문에만 적용된다. */
export const useUpdateOrderShippingAddress = () =>
  useMutation({
    mutationFn: ({ orderId, addressId }: UpdateOrderShippingAddressVariables) =>
      updateOrderShippingAddress(orderId, addressId),
  });

interface CancelOrderVariables {
  orderId: number;
  reason?: string;
  refundAccount?: RefundAccount;
}

export const useCancelOrder = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ orderId, reason, refundAccount }: CancelOrderVariables) =>
      cancelOrder(orderId, reason || refundAccount ? { reason, refundAccount } : undefined),
    onSuccess: (data, { orderId }) => {
      queryClient.setQueryData(orderKeys.detail(orderId), data);
    },
    // 실패해도 서버 상태가 바뀌었을 수 있어 끝나면 항상 무효화한다.
    onSettled: () => {
      // 취소로 재고가 복구되므로 목록과 장바구니 캐시도 함께 무효화한다.
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
      queryClient.invalidateQueries({ queryKey: cartKeys.all });
      // 취소로 쿠폰도 다시 사용 가능 상태가 되므로 함께 무효화한다.
      queryClient.invalidateQueries({ queryKey: couponKeys.all });
      queryClient.invalidateQueries({ queryKey: productKeys.details });
    },
  });
};

interface CancelOrderItemVariables {
  orderId: number;
  itemId: number;
  reason?: string;
  refundAccount?: RefundAccount;
}

/** 상품주문 상태가 바뀌면 상세(availableActions 포함)와 목록을 모두 다시 받는다. */
const useInvalidateOrders = () => {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: orderKeys.all });
    queryClient.invalidateQueries({ queryKey: orderKeys.detailAll });
  };
};

export const useCancelOrderItem = () => {
  const queryClient = useQueryClient();
  const invalidateOrders = useInvalidateOrders();

  return useMutation({
    mutationFn: ({ orderId, itemId, reason, refundAccount }: CancelOrderItemVariables) =>
      cancelOrderItem(
        orderId,
        itemId,
        reason || refundAccount ? { reason, refundAccount } : undefined,
      ),
    onSettled: () => {
      invalidateOrders();
      // 즉시 취소되면 재고·쿠폰이 되돌아온다.
      queryClient.invalidateQueries({ queryKey: cartKeys.all });
      queryClient.invalidateQueries({ queryKey: couponKeys.all });
      queryClient.invalidateQueries({ queryKey: productKeys.details });
    },
  });
};

interface ReturnOrderItemVariables {
  orderId: number;
  itemId: number;
  reason?: string;
  refundAccount?: RefundAccount;
}

export const useReturnOrderItem = () => {
  const invalidateOrders = useInvalidateOrders();

  return useMutation({
    mutationFn: ({ orderId, itemId, reason, refundAccount }: ReturnOrderItemVariables) =>
      returnOrderItem(
        orderId,
        itemId,
        reason || refundAccount ? { reason, refundAccount } : undefined,
      ),
    onSettled: () => invalidateOrders(),
  });
};

export const useWithdrawOrderClaim = () => {
  const invalidateOrders = useInvalidateOrders();

  return useMutation({
    mutationFn: (claimId: number) => withdrawOrderClaim(claimId),
    onSettled: () => invalidateOrders(),
  });
};

interface ConfirmOrderItemVariables {
  orderId: number;
  itemId: number;
  productId: number;
}

export const useConfirmOrderItem = () => {
  const queryClient = useQueryClient();
  const invalidateOrders = useInvalidateOrders();

  return useMutation({
    mutationFn: ({ orderId, itemId }: ConfirmOrderItemVariables) =>
      confirmOrderItem(orderId, itemId),
    onSuccess: (data, { orderId, productId }) => {
      queryClient.setQueryData(orderKeys.detail(orderId), data);
      queryClient.invalidateQueries({ queryKey: reviewKeys.eligibility(productId) });
    },
    onSettled: () => invalidateOrders(),
  });
};
