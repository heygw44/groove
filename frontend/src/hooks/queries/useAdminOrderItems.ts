import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getAdminOrderItems } from '@/api/admin';
import { adminOrderItemKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AdminOrderItemListParams } from '@/types/adminOrder';

export const useAdminOrderItems = (params: AdminOrderItemListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminOrderItemKeys.list(params),
    queryFn: () => getAdminOrderItems(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
