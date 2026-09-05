import { useQuery, type UseQueryOptions } from '@tanstack/react-query';

import { getOrder } from '@/api/order';
import { orderKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { OrderDetail } from '@/types/order';

type RefetchInterval = UseQueryOptions<OrderDetail>['refetchInterval'];

export const useOrder = (id: number, refetchInterval: RefetchInterval = false) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: orderKeys.detail(id),
    queryFn: () => getOrder(id),
    enabled: Boolean(accessToken) && !isBootstrapping && Number.isInteger(id) && id > 0,
    refetchInterval,
  });
};
