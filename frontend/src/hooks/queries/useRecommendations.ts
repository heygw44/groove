import { useQuery } from '@tanstack/react-query';

import { getHomeRecommendations, getRelatedProducts } from '@/api/recommend';
import { HOME_RECOMMEND_SIZE, RELATED_PRODUCT_SIZE } from '@/constants/recommendReasons';
import { recommendKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

const FIVE_MINUTES = 5 * 60 * 1000;

export const useHomeRecommendations = () => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: recommendKeys.home,
    queryFn: () => getHomeRecommendations(HOME_RECOMMEND_SIZE),
    enabled: Boolean(accessToken) && !isBootstrapping,
    staleTime: FIVE_MINUTES,
  });
};

export const useRelatedProducts = (productId: number) => {
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: recommendKeys.related(productId),
    queryFn: () => getRelatedProducts(productId, RELATED_PRODUCT_SIZE),
    // 응답의 wishlisted 가 로그인 여부로 갈린다 — useProducts 와 같은 이유.
    enabled: !isBootstrapping,
    staleTime: FIVE_MINUTES,
  });
};
