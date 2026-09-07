import { useQuery } from '@tanstack/react-query';

import { getUnreadNotificationCount } from '@/api/notification';
import { notificationKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

// 뱃지가 실시간일 필요는 없지만 방치되면 안 되니 30초마다 갱신한다.
const UNREAD_COUNT_REFETCH_INTERVAL_MS = 30_000;

export const useUnreadNotificationCount = () => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: notificationKeys.unreadCount,
    queryFn: getUnreadNotificationCount,
    // 비로그인 상태로는 401 이 될 뿐이라 애초에 요청을 보내지 않는다.
    enabled: Boolean(accessToken) && !isBootstrapping,
    refetchInterval: UNREAD_COUNT_REFETCH_INTERVAL_MS,
  });
};
