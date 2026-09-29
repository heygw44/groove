import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getAdminOrderClaims } from '@/api/admin';
import { adminOrderClaimKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AdminOrderClaimListParams } from '@/types/adminOrder';

export const useAdminOrderClaims = (params: AdminOrderClaimListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminOrderClaimKeys.list(params),
    queryFn: () => getAdminOrderClaims(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
