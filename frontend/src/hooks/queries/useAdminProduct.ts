import { useQuery } from '@tanstack/react-query';

import { getAdminProduct } from '@/api/admin';
import { adminProductKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useAdminProduct = (id: number) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminProductKeys.detail(id),
    queryFn: () => getAdminProduct(id),
    enabled: Boolean(accessToken) && !isBootstrapping && Number.isInteger(id) && id > 0,
  });
};
