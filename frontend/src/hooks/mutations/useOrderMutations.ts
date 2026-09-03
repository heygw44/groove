import { useMutation, useQueryClient } from '@tanstack/react-query';

import { createOrder } from '@/api/order';
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
