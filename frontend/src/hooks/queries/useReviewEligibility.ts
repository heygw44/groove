import { useQuery } from '@tanstack/react-query';

import { getReviewEligibility } from '@/api/review';
import { reviewKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useReviewEligibility = (productId: number) => {
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: reviewKeys.eligibility(productId),
    queryFn: () => getReviewEligibility(productId),
    // 로그인 여부로 결과가 갈려 부팅 재발급 전엔 쏘지 않는다.
    enabled: productId > 0 && !isBootstrapping,
  });
};
