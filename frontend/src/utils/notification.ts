import type { NotificationItem, NotificationType } from '@/types/notification';

/** readAt 이 없으면(키 자체가 빠지거나 null) 안 읽은 알림이다. */
export const isUnreadNotification = (item: NotificationItem): boolean =>
  item.readAt === undefined || item.readAt === null;

const MESSAGE_BUILDERS: Record<NotificationType, (titleSnapshot: string) => string> = {
  RESTOCK: (titleSnapshot) => `${titleSnapshot} 재입고됐어요`,
  PRICE_DROP: (titleSnapshot) => `${titleSnapshot} 가격이 내려갔어요`,
  NEW_PRESSING: (titleSnapshot) => `${titleSnapshot}의 새 프레싱이 등록됐어요`,
};

export const buildNotificationMessage = (item: NotificationItem): string =>
  MESSAGE_BUILDERS[item.type](item.titleSnapshot);

export const buildNotificationLink = (item: NotificationItem): string | undefined => {
  if (item.type === 'NEW_PRESSING') {
    return item.albumId !== undefined ? `/albums/${item.albumId}` : undefined;
  }
  return item.productId !== undefined ? `/products/${item.productId}` : undefined;
};
