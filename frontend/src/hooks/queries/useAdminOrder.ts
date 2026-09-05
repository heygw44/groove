import { useQuery } from '@tanstack/react-query';

import { getAdminOrder } from '@/api/admin';
import { adminOrderKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useAdminOrder = (orderId: number) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminOrderKeys.detail(orderId),
    queryFn: () => getAdminOrder(orderId),
    enabled: Boolean(accessToken) && !isBootstrapping && Number.isInteger(orderId) && orderId > 0,
  });
};
