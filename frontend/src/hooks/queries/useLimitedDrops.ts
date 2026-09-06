import { useQuery } from '@tanstack/react-query';

import { getLimitedDrops } from '@/api/limitedDrop';
import { limitedDropKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { LimitedDropStatus } from '@/types/limitedDrop';

export const useLimitedDrops = (status?: LimitedDropStatus) => {
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: limitedDropKeys.list(status),
    queryFn: () => getLimitedDrops(status),
    // 응답의 tasteMatch 가 로그인 여부로 갈린다. 부팅 재발급 전에 쏘면 토큰 없이 나가
    // 배지 없는 목록이 캐시되고, 탭 전환은 클라이언트 필터라 다시 받지 않는다(useProducts 와 같은 이유).
    enabled: !isBootstrapping,
    // 서버가 Cache-Control: no-store 로 내려준다 - 캐시를 오래 들고 있을 이유가 없다.
    staleTime: 0,
  });
};
