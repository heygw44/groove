import { useQuery } from '@tanstack/react-query';

import { getCart } from '@/api/cart';
import { cartKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useCart = () => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: cartKeys.all,
    queryFn: getCart,
    enabled: Boolean(accessToken) && !isBootstrapping,
  });
};
