import { useQuery } from '@tanstack/react-query';

import { getMe } from '@/api/member';
import { memberKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useMe = () => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: memberKeys.me,
    queryFn: () => getMe(),
    /* 부팅 재발급이 끝나기 전에 쏘면 토큰 없이 나가 401 을 받는다. */
    enabled: Boolean(accessToken) && !isBootstrapping,
  });
};
