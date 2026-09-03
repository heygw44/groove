import { useQuery } from '@tanstack/react-query';

import { getProduct } from '@/api/product';
import { productKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useProduct = (id: number) => {
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: productKeys.detail(id),
    queryFn: () => getProduct(id),
    // 부팅 재발급 전에 쏘면 토큰 없이 나가 wishlisted 가 빠진 채 캐시된다.
    enabled: Number.isInteger(id) && id > 0 && !isBootstrapping,
  });
};
