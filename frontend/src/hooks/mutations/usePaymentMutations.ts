import { useMutation, useQueryClient } from '@tanstack/react-query';

import { confirmPayment } from '@/api/payment';
import { orderKeys } from '@/hooks/queries/queryKeys';
import type { PaymentConfirmRequest } from '@/types/payment';

export const useConfirmPayment = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: PaymentConfirmRequest) => confirmPayment(payload),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
      queryClient.invalidateQueries({ queryKey: orderKeys.detail(data.orderId) });
    },
  });
};
