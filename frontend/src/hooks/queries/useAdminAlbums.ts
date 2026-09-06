import { useQuery } from '@tanstack/react-query';

import { getAdminAlbums } from '@/api/admin';
import { adminAlbumKeys } from '@/hooks/queries/queryKeys';
import type { AdminAlbumListParams } from '@/types/product';

export const useAdminAlbums = (params: AdminAlbumListParams, enabled = true) =>
  useQuery({
    queryKey: adminAlbumKeys.list(params),
    queryFn: () => getAdminAlbums(params),
    enabled,
  });
