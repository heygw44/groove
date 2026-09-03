import { useMutation, useQueryClient } from '@tanstack/react-query';

import { cancelOrder, createOrder } from '@/api/order';
import { cartKeys, orderKeys } from '@/hooks/queries/queryKeys';
import type { OrderCreateRequest } from '@/types/order';

export const useCreateOrder = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: OrderCreateRequest) => createOrder(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cartKeys.all });
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
    },
  });
};

interface CancelOrderVariables {
  orderId: number;
  reason?: string;
}

export const useCancelOrder = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ orderId, reason }: CancelOrderVariables) =>
      cancelOrder(orderId, reason ? { reason } : undefined),
    onSuccess: (data, { orderId }) => {
      queryClient.setQueryData(orderKeys.detail(orderId), data);
      // 취소로 재고가 복구되므로 목록과 장바구니 캐시도 함께 무효화한다.
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
      queryClient.invalidateQueries({ queryKey: cartKeys.all });
    },
  });
};
