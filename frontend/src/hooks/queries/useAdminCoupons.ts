import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getAdminCoupons } from '@/api/admin';
import { adminCouponKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AdminCouponListParams } from '@/types/coupon';

export const useAdminCoupons = (params: AdminCouponListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminCouponKeys.list(params),
    queryFn: () => getAdminCoupons(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
