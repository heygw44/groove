import { useQuery } from '@tanstack/react-query';

import { getLimitedDrop } from '@/api/limitedDrop';
import { limitedDropKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

const OPEN_REFETCH_INTERVAL_MS = 5_000;

export const useLimitedDrop = (id: number) => {
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: limitedDropKeys.detail(id),
    queryFn: () => getLimitedDrop(id),
    // purchased 가 로그인 의존 필드라 부팅 재발급 전에 쏘면 빠진 채 캐시된다 (useProduct.ts 와 같은 이유).
    enabled: Number.isInteger(id) && id > 0 && !isBootstrapping,
    staleTime: 0,
    // OPEN 인 동안엔 재고 소진을 실시간에 가깝게 반영한다.
    refetchInterval: (query) =>
      query.state.data?.status === 'OPEN' ? OPEN_REFETCH_INTERVAL_MS : false,
  });
};
