import { useQuery } from '@tanstack/react-query';

import { getRecentViews } from '@/api/recommend';
import { recentViewKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useRecentViews = () => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: recentViewKeys.all,
    queryFn: getRecentViews,
    enabled: Boolean(accessToken) && !isBootstrapping,
    // 상품 상세를 볼 때마다 순서가 바뀌므로 마운트마다 새로 받는다.
    staleTime: 0,
  });
};
