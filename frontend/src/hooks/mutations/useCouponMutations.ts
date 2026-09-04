import { useMutation, useQueryClient } from '@tanstack/react-query';

import { issueCoupon } from '@/api/coupon';
import { couponKeys } from '@/hooks/queries/queryKeys';

export const useIssueCoupon = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: issueCoupon,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: couponKeys.all });
    },
  });
};
