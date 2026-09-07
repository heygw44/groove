import type { QueryKey } from '@tanstack/react-query';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  deleteNotification,
  deleteReadNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notification';
import { notificationKeys } from '@/hooks/queries/queryKeys';
import type { PageResponse } from '@/types/api';
import type { NotificationItem, UnreadCount } from '@/types/notification';
import { getErrorCode } from '@/utils/apiError';
import { isUnreadNotification } from '@/utils/notification';

interface NotificationMutationContext {
  previousLists: Array<[QueryKey, PageResponse<NotificationItem> | undefined]>;
  previousUnreadCount: UnreadCount | undefined;
}

const rollback = (
  queryClient: ReturnType<typeof useQueryClient>,
  context: NotificationMutationContext | undefined,
) => {
  if (!context) {
    return;
  }
  context.previousLists.forEach(([key, data]) => {
    queryClient.setQueryData(key, data);
  });
  queryClient.setQueryData(notificationKeys.unreadCount, context.previousUnreadCount);
};

const snapshotNotifications = (
  queryClient: ReturnType<typeof useQueryClient>,
): NotificationMutationContext => ({
  previousLists: queryClient.getQueriesData<PageResponse<NotificationItem>>({
    queryKey: notificationKeys.lists,
  }),
  previousUnreadCount: queryClient.getQueryData<UnreadCount>(notificationKeys.unreadCount),
});

export const useMarkNotificationRead = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id }: { id: number }) => markNotificationRead(id),
    // 벨 아이콘을 누른 즉시 읽음 처리가 보이도록 목록과 뱃지를 먼저 갱신한다.
    onMutate: async ({ id }): Promise<NotificationMutationContext> => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.lists });
      await queryClient.cancelQueries({ queryKey: notificationKeys.unreadCount });

      const context = snapshotNotifications(queryClient);
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

      return context;
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
    onMutate: async (): Promise<NotificationMutationContext> => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.lists });
      await queryClient.cancelQueries({ queryKey: notificationKeys.unreadCount });

      const context = snapshotNotifications(queryClient);
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

      return context;
    },
    onError: (_error, _variables, context) => rollback(queryClient, context),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
};

export const useDeleteNotification = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id }: { id: number }) => deleteNotification(id),
    // 삭제 즉시 목록/뱃지에서 사라지도록 낙관적으로 반영한다.
    onMutate: async ({ id }): Promise<NotificationMutationContext> => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.lists });
      await queryClient.cancelQueries({ queryKey: notificationKeys.unreadCount });

      const context = snapshotNotifications(queryClient);
      const deletedItem = context.previousLists
        .flatMap(([, data]) => data?.content ?? [])
        .find((item) => item.id === id);
      const wasUnread = deletedItem !== undefined && isUnreadNotification(deletedItem);

      queryClient.setQueriesData<PageResponse<NotificationItem>>(
        { queryKey: notificationKeys.lists },
        (old) =>
          old && {
            ...old,
            content: old.content.filter((item) => item.id !== id),
            totalElements: Math.max(0, old.totalElements - 1),
          },
      );
      if (wasUnread) {
        queryClient.setQueryData<UnreadCount>(notificationKeys.unreadCount, (old) => ({
          count: Math.max(0, (old?.count ?? 0) - 1),
        }));
      }

      return context;
    },
    onError: (error, _variables, context) => {
      // 이미 서버에서 사라졌다는 뜻이라 낙관적으로 반영한 값이 곧 서버 상태다.
      if (getErrorCode(error) === 'NOTIFICATION_NOT_FOUND') {
        return;
      }
      rollback(queryClient, context);
    },
    // 페이지네이션이라 totalPages 가 낙관적 갱신만으로는 어긋나므로 무효화가 필수다.
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
};

export const useDeleteReadNotifications = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deleteReadNotifications,
    // 다른 페이지에 걸친 항목까지 지워 결과를 예측할 수 없으니 낙관적 갱신 없이 재조회한다.
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
};
