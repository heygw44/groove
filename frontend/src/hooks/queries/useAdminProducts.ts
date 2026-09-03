import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getAdminProducts } from '@/api/admin';
import { adminProductKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AdminProductListParams } from '@/types/product';

export const useAdminProducts = (params: AdminProductListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminProductKeys.list(params),
    queryFn: () => getAdminProducts(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
