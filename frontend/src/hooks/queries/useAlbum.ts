import { useQuery } from '@tanstack/react-query';

import { getAlbum } from '@/api/albums';
import { albumKeys } from '@/hooks/queries/queryKeys';

const FIVE_MINUTES = 5 * 60 * 1000;

interface UseAlbumOptions {
  enabled?: boolean;
}

// 프레싱 목록에는 서버가 wishlisted 를 내려주지 않아 로그인 여부로 응답이 갈리지 않는다.
// 다른 공개 쿼리와 달리 isBootstrapping 을 기다릴 이유가 없어 바로 조회한다.
// options.enabled 는 호출자가 프레싱이 하나뿐인 걸 이미 알아 조회 자체를 끄고 싶을 때 쓴다.
export const useAlbum = (id: number, options?: UseAlbumOptions) =>
  useQuery({
    queryKey: albumKeys.detail(id),
    queryFn: () => getAlbum(id),
    enabled: Number.isInteger(id) && id > 0 && (options?.enabled ?? true),
    staleTime: FIVE_MINUTES,
  });
