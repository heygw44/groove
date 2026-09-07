import { useQuery } from '@tanstack/react-query';

import { getLimitedDrops } from '@/api/limitedDrop';
import { limitedDropKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { LimitedDropStatus } from '@/types/limitedDrop';
import { getDropPhase } from '@/utils/limitedDrop';
import { getServerNowMs } from '@/utils/serverTime';

// 상세 페이지(OPENING_POLL_MS)와 같은 간격 - 목록/배너에도 폴링이 없으면 status 갱신 전까지
// "예정 / 00:00:00" 이 새로고침 전까지 고정된다.
const OPENING_POLL_MS = 3_000;

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
    refetchInterval: (query) => {
      const drops = query.state.data?.drops ?? [];
      const hasOpening = drops.some((drop) => getDropPhase(drop, getServerNowMs()) === 'OPENING');
      return hasOpening ? OPENING_POLL_MS : false;
    },
  });
};
