import { useQuery } from '@tanstack/react-query';

import { getAlbumWatches } from '@/api/albumWatch';
import { albumWatchKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useAlbumWatches = () => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: albumWatchKeys.all,
    queryFn: getAlbumWatches,
    enabled: Boolean(accessToken) && !isBootstrapping,
  });
};
