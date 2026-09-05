import { useQuery } from '@tanstack/react-query';

import { getLimitedDrops } from '@/api/limitedDrop';
import { limitedDropKeys } from '@/hooks/queries/queryKeys';
import type { LimitedDropStatus } from '@/types/limitedDrop';

export const useLimitedDrops = (status?: LimitedDropStatus) =>
  useQuery({
    queryKey: limitedDropKeys.list(status),
    queryFn: () => getLimitedDrops(status),
    // 서버가 Cache-Control: no-store 로 내려준다 - 캐시를 오래 들고 있을 이유가 없다.
    staleTime: 0,
  });
