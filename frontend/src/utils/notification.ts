import type { NotificationItem, NotificationType } from '@/types/notification';

/** readAt 이 없으면(키 자체가 빠지거나 null) 안 읽은 알림이다. */
export const isUnreadNotification = (item: NotificationItem): boolean =>
  item.readAt === undefined || item.readAt === null;

const MESSAGE_BUILDERS: Record<NotificationType, (titleSnapshot: string) => string> = {
  RESTOCK: (titleSnapshot) => `${titleSnapshot} 재입고됐습니다`,
  PRICE_DROP: (titleSnapshot) => `${titleSnapshot} 가격이 내려갔습니다`,
  NEW_PRESSING: (titleSnapshot) => `${titleSnapshot}의 새 에디션이 등록됐습니다`,
};

export const buildNotificationMessage = (item: NotificationItem): string =>
  MESSAGE_BUILDERS[item.type](item.titleSnapshot);

export const buildNotificationLink = (item: NotificationItem): string | undefined => {
  if (item.type === 'NEW_PRESSING') {
    return item.albumId !== undefined ? `/albums/${item.albumId}` : undefined;
  }
  return item.productId !== undefined ? `/products/${item.productId}` : undefined;
};

export const MAX_DISPLAY_COUNT = 99;

/** 배지에 찍기엔 너무 큰 안 읽음 수는 상한 뒤에 + 를 붙여 자른다. */
export const formatBadgeCount = (count: number): string =>
  count > MAX_DISPLAY_COUNT ? `${MAX_DISPLAY_COUNT}+` : String(count);
