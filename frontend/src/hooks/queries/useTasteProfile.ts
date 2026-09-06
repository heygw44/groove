import { useQuery } from '@tanstack/react-query';

import { getTasteProfile } from '@/api/recommend';
import { tasteProfileKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { TasteProfile } from '@/types/recommend';
import { getErrorCode } from '@/utils/apiError';

export const useTasteProfile = () => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery<TasteProfile | null>({
    queryKey: tasteProfileKeys.mine,
    queryFn: async () => {
      try {
        return await getTasteProfile();
      } catch (error) {
        // 프로필 없음(404 RECOMMEND_PROFILE_NOT_FOUND)은 온보딩 전 정상 상태다.
        // v5 는 undefined 를 실패로 보므로 null 로 캐시한다.
        if (getErrorCode(error) === 'RECOMMEND_PROFILE_NOT_FOUND') {
          return null;
        }
        throw error;
      }
    },
    enabled: Boolean(accessToken) && !isBootstrapping,
  });
};
