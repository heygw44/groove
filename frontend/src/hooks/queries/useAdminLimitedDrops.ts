import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getAdminLimitedDrop, getAdminLimitedDrops } from '@/api/admin';
import { adminLimitedDropKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AdminLimitedDropListParams } from '@/types/limitedDrop';

const OPEN_REFETCH_INTERVAL_MS = 5_000;

export const useAdminLimitedDrops = (params: AdminLimitedDropListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminLimitedDropKeys.list(params),
    queryFn: () => getAdminLimitedDrops(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
    // OPEN 인 드롭이 있으면 판매 현황이 실시간에 가깝게 바뀌므로 목록도 갱신한다.
    refetchInterval: (query) =>
      query.state.data?.content.some((drop) => drop.status === 'OPEN')
        ? OPEN_REFETCH_INTERVAL_MS
        : false,
  });
};

export const useAdminLimitedDrop = (id: number | undefined) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminLimitedDropKeys.detail(id ?? 0),
    queryFn: () => getAdminLimitedDrop(id as number),
    enabled: Boolean(accessToken) && !isBootstrapping && id !== undefined,
    refetchInterval: (query) =>
      query.state.data?.status === 'OPEN' ? OPEN_REFETCH_INTERVAL_MS : false,
  });
};
