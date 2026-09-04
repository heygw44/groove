import { useQuery } from '@tanstack/react-query';

import { getMyCoupons } from '@/api/coupon';
import { couponKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { MemberCouponStatus } from '@/types/coupon';

export const useMyCoupons = (status?: MemberCouponStatus) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: couponKeys.mine(status),
    queryFn: () => getMyCoupons(status),
    enabled: Boolean(accessToken) && !isBootstrapping,
  });
};
