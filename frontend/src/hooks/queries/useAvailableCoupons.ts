import { useQuery } from '@tanstack/react-query';

import { getAvailableCoupons } from '@/api/coupon';
import { couponKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useAvailableCoupons = (orderAmount: number) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: couponKeys.available(orderAmount),
    queryFn: () => getAvailableCoupons(orderAmount),
    enabled: Boolean(accessToken) && !isBootstrapping && orderAmount > 0,
  });
};
