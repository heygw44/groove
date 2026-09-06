import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getCatalogImportJobs } from '@/api/catalog';
import { adminCatalogImportJobKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { CatalogImportJobListParams } from '@/types/catalog';

const RUNNING_REFETCH_INTERVAL_MS = 3_000;

export const useAdminCatalogImportJobs = (params: CatalogImportJobListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminCatalogImportJobKeys.list(params),
    queryFn: () => getCatalogImportJobs(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
    // 진행 중인 잡이 있으면 진행 카운트가 실시간에 가깝게 바뀌므로 목록도 갱신한다.
    // refetchIntervalInBackground 는 기본 false 라 탭이 숨겨지면 폴링이 멈춘다 - 의도한 동작.
    refetchInterval: (query) =>
      query.state.data?.content.some((job) => job.status === 'STARTING' || job.status === 'STARTED')
        ? RUNNING_REFETCH_INTERVAL_MS
        : false,
  });
};
