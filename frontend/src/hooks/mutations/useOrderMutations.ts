import { useMutation, useQueryClient } from '@tanstack/react-query';

import { cancelOrder, createOrder, updateOrderShippingAddress } from '@/api/order';
import { cartKeys, couponKeys, orderKeys } from '@/hooks/queries/queryKeys';
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
      // 취소로 재고가 복구되므로 목록과 장바구니 캐시도 함께 무효화한다.
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
      queryClient.invalidateQueries({ queryKey: cartKeys.all });
      // 취소로 쿠폰도 다시 사용 가능 상태가 되므로 함께 무효화한다.
      queryClient.invalidateQueries({ queryKey: couponKeys.all });
    },
  });
};
