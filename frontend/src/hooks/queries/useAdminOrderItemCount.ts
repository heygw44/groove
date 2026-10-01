import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getAdminOrderItemCount } from '@/api/admin';
import { adminOrderItemKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AdminOrderItemCountParams } from '@/types/adminOrder';

export const useAdminOrderItemCount = (params: AdminOrderItemCountParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminOrderItemKeys.count(params),
    queryFn: () => getAdminOrderItemCount(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
