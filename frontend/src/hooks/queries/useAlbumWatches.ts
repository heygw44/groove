import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getAlbumWatches } from '@/api/albumWatch';
import { albumWatchKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AlbumWatchListParams } from '@/types/albumWatch';

export const useAlbumWatches = (params: AlbumWatchListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: albumWatchKeys.list(params),
    queryFn: () => getAlbumWatches(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
