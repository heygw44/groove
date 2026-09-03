import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getWishlist } from '@/api/wishlist';
import { wishlistKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { WishlistListParams } from '@/types/wishlist';

export const useWishlist = (params: WishlistListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: wishlistKeys.list(params),
    queryFn: () => getWishlist(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
