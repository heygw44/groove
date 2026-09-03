import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getProducts } from '@/api/product';
import { productKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { ProductListParams } from '@/types/product';

export const useProducts = (params: ProductListParams) => {
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: productKeys.list(params),
    queryFn: () => getProducts(params),
    // 부팅 재발급 전에 쏘면 토큰 없이 나가 wishlisted 가 빠진 채 캐시된다.
    enabled: !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
