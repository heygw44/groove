import { useQuery } from '@tanstack/react-query';

import { getAlbum } from '@/api/albums';
import { albumKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

const FIVE_MINUTES = 5 * 60 * 1000;

interface UseAlbumOptions {
  enabled?: boolean;
}

// watched 는 로그인 여부로 응답이 갈리므로(비로그인 null) 부팅 재발급 전에 쏘면 캐시가
// 잘못된 값으로 굳는다. options.enabled 는 호출자가 프레싱이 하나뿐인 걸 이미 알아
// 조회 자체를 끄고 싶을 때 쓴다.
export const useAlbum = (id: number, options?: UseAlbumOptions) => {
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: albumKeys.detail(id),
    queryFn: () => getAlbum(id),
    enabled: Number.isInteger(id) && id > 0 && !isBootstrapping && (options?.enabled ?? true),
    staleTime: FIVE_MINUTES,
  });
};
