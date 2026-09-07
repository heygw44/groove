import { client, unwrap } from '@/api/client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type { NotificationItem, NotificationListParams, UnreadCount } from '@/types/notification';

export const getNotifications = (params: NotificationListParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<NotificationItem>>>('/members/me/notifications', {
      params,
    }),
  );

export const getUnreadNotificationCount = () =>
  unwrap(client.get<ApiResponse<UnreadCount>>('/members/me/notifications/unread-count'));

export const markNotificationRead = async (id: number) => {
  await client.patch<ApiResponse<void>>(`/notifications/${id}/read`);
};

export const markAllNotificationsRead = async () => {
  await client.patch<ApiResponse<void>>('/members/me/notifications/read-all');
};
