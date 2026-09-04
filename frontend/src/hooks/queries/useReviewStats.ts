import { useQuery } from '@tanstack/react-query';

import { getReviewStats } from '@/api/review';
import { reviewKeys } from '@/hooks/queries/queryKeys';

export const useReviewStats = (productId: number) =>
  // 로그인 여부와 무관한 공개 데이터라 부팅 게이트가 필요 없다.
  useQuery({
    queryKey: reviewKeys.stats(productId),
    queryFn: () => getReviewStats(productId),
    enabled: productId > 0,
  });
