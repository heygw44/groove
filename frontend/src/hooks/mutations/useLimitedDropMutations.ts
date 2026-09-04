import { useMutation, useQueryClient } from '@tanstack/react-query';

import { purchaseLimitedDrop } from '@/api/limitedDrop';
import { limitedDropKeys, orderKeys } from '@/hooks/queries/queryKeys';
import type { LimitedPurchaseRequest } from '@/types/limitedDrop';

export const usePurchaseLimitedDrop = (dropId: number) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: LimitedPurchaseRequest) => purchaseLimitedDrop(dropId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: limitedDropKeys.detail(dropId) });
      // 목록의 remainingQuantity/status 도 같이 바뀌므로 함께 무효화한다.
      queryClient.invalidateQueries({ queryKey: limitedDropKeys.all });
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
    },
  });
};
