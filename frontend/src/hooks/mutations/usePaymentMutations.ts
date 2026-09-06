import { useMutation, useQueryClient } from '@tanstack/react-query';

import { confirmPayment } from '@/api/payment';
import { orderKeys, recommendKeys } from '@/hooks/queries/queryKeys';
import type { PaymentConfirmRequest } from '@/types/payment';

export const useConfirmPayment = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: PaymentConfirmRequest) => confirmPayment(payload),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
      queryClient.invalidateQueries({ queryKey: orderKeys.detail(data.orderId) });
      // 추천은 구매 신호를 서버가 반영하므로 함께 무효화한다.
      queryClient.invalidateQueries({ queryKey: recommendKeys.all });
    },
  });
};
