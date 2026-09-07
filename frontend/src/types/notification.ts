export type NotificationType = 'RESTOCK' | 'PRICE_DROP' | 'NEW_PRESSING';

/** 서버가 non_null 로 직렬화해 해당 없는 필드는 키 자체가 빠진다. */
export interface NotificationItem {
  id: number;
  type: NotificationType;
  productId?: number;
  albumId?: number;
  titleSnapshot: string;
  readAt?: string;
  createdAt: string;
}

export interface NotificationListParams {
  page?: number;
  size?: number;
  unreadOnly?: boolean;
}

export interface UnreadCount {
  count: number;
}
