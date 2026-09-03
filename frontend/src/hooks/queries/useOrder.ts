import { useQuery } from '@tanstack/react-query';

import { getOrder } from '@/api/order';
import { orderKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useOrder = (id: number) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: orderKeys.detail(id),
    queryFn: () => getOrder(id),
    enabled: Boolean(accessToken) && !isBootstrapping && Number.isInteger(id) && id > 0,
  });
};
