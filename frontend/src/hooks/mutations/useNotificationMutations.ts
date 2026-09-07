import type { QueryKey } from '@tanstack/react-query';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { markAllNotificationsRead, markNotificationRead } from '@/api/notification';
import { notificationKeys } from '@/hooks/queries/queryKeys';
import type { PageResponse } from '@/types/api';
import type { NotificationItem, UnreadCount } from '@/types/notification';

interface ReadMutationContext {
  previousLists: Array<[QueryKey, PageResponse<NotificationItem> | undefined]>;
  previousUnreadCount: UnreadCount | undefined;
}

const rollback = (
  queryClient: ReturnType<typeof useQueryClient>,
  context: ReadMutationContext | undefined,
) => {
  if (!context) {
    return;
  }
  context.previousLists.forEach(([key, data]) => {
    queryClient.setQueryData(key, data);
  });
  queryClient.setQueryData(notificationKeys.unreadCount, context.previousUnreadCount);
};

export const useMarkNotificationRead = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id }: { id: number }) => markNotificationRead(id),
    // 벨 아이콘을 누른 즉시 읽음 처리가 보이도록 목록과 뱃지를 먼저 갱신한다.
    onMutate: async ({ id }): Promise<ReadMutationContext> => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.lists });
      await queryClient.cancelQueries({ queryKey: notificationKeys.unreadCount });

      const previousLists = queryClient.getQueriesData<PageResponse<NotificationItem>>({
        queryKey: notificationKeys.lists,
      });
      const previousUnreadCount = queryClient.getQueryData<UnreadCount>(
        notificationKeys.unreadCount,
      );
      const readAt = new Date().toISOString();

      queryClient.setQueriesData<PageResponse<NotificationItem>>(
        { queryKey: notificationKeys.lists },
        (old) =>
          old && {
            ...old,
            content: old.content.map((item) => (item.id === id ? { ...item, readAt } : item)),
          },
      );
      queryClient.setQueryData<UnreadCount>(notificationKeys.unreadCount, (old) => ({
        count: Math.max(0, (old?.count ?? 0) - 1),
      }));

      return { previousLists, previousUnreadCount };
    },
    onError: (_error, _variables, context) => rollback(queryClient, context),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
};

export const useMarkAllNotificationsRead = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: markAllNotificationsRead,
    onMutate: async (): Promise<ReadMutationContext> => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.lists });
      await queryClient.cancelQueries({ queryKey: notificationKeys.unreadCount });

      const previousLists = queryClient.getQueriesData<PageResponse<NotificationItem>>({
        queryKey: notificationKeys.lists,
      });
      const previousUnreadCount = queryClient.getQueryData<UnreadCount>(
        notificationKeys.unreadCount,
      );
      const readAt = new Date().toISOString();

      queryClient.setQueriesData<PageResponse<NotificationItem>>(
        { queryKey: notificationKeys.lists },
        (old) =>
          old && {
            ...old,
            content: old.content.map((item) => ({ ...item, readAt })),
          },
      );
      queryClient.setQueryData<UnreadCount>(notificationKeys.unreadCount, { count: 0 });

      return { previousLists, previousUnreadCount };
    },
    onError: (_error, _variables, context) => rollback(queryClient, context),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
};
