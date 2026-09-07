import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getNotifications } from '@/api/notification';
import { notificationKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { NotificationListParams } from '@/types/notification';

export const useNotifications = (params: NotificationListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: notificationKeys.list(params),
    queryFn: () => getNotifications(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
