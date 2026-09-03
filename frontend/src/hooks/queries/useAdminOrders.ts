import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getAdminOrders } from '@/api/admin';
import { adminOrderKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AdminOrderListParams } from '@/types/order';

export const useAdminOrders = (params: AdminOrderListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminOrderKeys.list(params),
    queryFn: () => getAdminOrders(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
