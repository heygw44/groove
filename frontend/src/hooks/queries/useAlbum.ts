import { useQuery } from '@tanstack/react-query';

import { getAlbum } from '@/api/albums';
import { albumKeys } from '@/hooks/queries/queryKeys';

const FIVE_MINUTES = 5 * 60 * 1000;

// 프레싱 목록에는 서버가 wishlisted 를 내려주지 않아 로그인 여부로 응답이 갈리지 않는다.
// 다른 공개 쿼리와 달리 isBootstrapping 을 기다릴 이유가 없어 바로 조회한다.
export const useAlbum = (id: number) =>
  useQuery({
    queryKey: albumKeys.detail(id),
    queryFn: () => getAlbum(id),
    enabled: Number.isInteger(id) && id > 0,
    staleTime: FIVE_MINUTES,
  });
