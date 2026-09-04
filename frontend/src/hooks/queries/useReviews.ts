import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getReviews } from '@/api/review';
import { reviewKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { ReviewListParams } from '@/types/review';

export const useReviews = (productId: number, params: ReviewListParams) => {
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: reviewKeys.list(productId, params),
    queryFn: () => getReviews(productId, params),
    // 공개 API 지만 mine 이 로그인 여부에 따라 달라져 부팅 재발급 전엔 쏘지 않는다.
    enabled: productId > 0 && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
