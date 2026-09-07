import { describe, expect, it } from 'vitest';

import type { NotificationItem } from '@/types/notification';
import {
  buildNotificationLink,
  buildNotificationMessage,
  formatBadgeCount,
  isUnreadNotification,
} from '@/utils/notification';

const item = (overrides: Partial<NotificationItem> = {}): NotificationItem => ({
  id: 1,
  type: 'RESTOCK',
  titleSnapshot: 'Kind of Blue',
  createdAt: '2026-09-07T00:00:00Z',
  ...overrides,
});

describe('buildNotificationMessage()', () => {
  it('RESTOCK 이면 재입고 문구를 만든다', () => {
    // given
    const notification = item({ type: 'RESTOCK', productId: 1 });

    // when
    const message = buildNotificationMessage(notification);

    // then
    expect(message).toBe('Kind of Blue 재입고됐습니다');
  });

  it('PRICE_DROP 이면 가격 인하 문구를 만든다', () => {
    // given
    const notification = item({ type: 'PRICE_DROP', productId: 1 });

    // when
    const message = buildNotificationMessage(notification);

    // then
    expect(message).toBe('Kind of Blue 가격이 내려갔습니다');
  });

  it('NEW_PRESSING 이면 새 에디션 문구를 만든다', () => {
    // given
    const notification = item({ type: 'NEW_PRESSING', albumId: 1, titleSnapshot: 'Nevermind' });

    // when
    const message = buildNotificationMessage(notification);

    // then
    expect(message).toBe('Nevermind의 새 에디션이 등록됐습니다');
  });
});

describe('isUnreadNotification()', () => {
  it('readAt 키 자체가 없으면 안 읽음으로 본다', () => {
    // given
    const notification = item();

    // when & then
    expect(isUnreadNotification(notification)).toBe(true);
  });

  it('readAt 이 null 이어도 안 읽음으로 본다', () => {
    // given
    const notification = item({ readAt: null as unknown as undefined });

    // when & then
    expect(isUnreadNotification(notification)).toBe(true);
  });

  it('readAt 이 있으면 읽음으로 본다', () => {
    // given
    const notification = item({ readAt: '2026-09-07T01:00:00Z' });

    // when & then
    expect(isUnreadNotification(notification)).toBe(false);
  });
});

describe('buildNotificationLink()', () => {
  it('RESTOCK 이고 productId 가 있으면 상품 상세 링크를 만든다', () => {
    // given
    const notification = item({ type: 'RESTOCK', productId: 7 });

    // when & then
    expect(buildNotificationLink(notification)).toBe('/products/7');
  });

  it('NEW_PRESSING 이고 albumId 가 있으면 앨범 상세 링크를 만든다', () => {
    // given
    const notification = item({ type: 'NEW_PRESSING', albumId: 9 });

    // when & then
    expect(buildNotificationLink(notification)).toBe('/albums/9');
  });

  it('링크에 필요한 id 가 없으면 undefined 를 돌려준다', () => {
    // given
    const notification = item({ type: 'PRICE_DROP' });

    // when & then
    expect(buildNotificationLink(notification)).toBeUndefined();
  });
});

describe('formatBadgeCount()', () => {
  it('99 이하면 숫자를 그대로 문자열로 돌려준다', () => {
    // when & then
    expect(formatBadgeCount(99)).toBe('99');
  });

  it('99 를 넘으면 99+ 로 자른다', () => {
    // when & then
    expect(formatBadgeCount(1234)).toBe('99+');
  });
});
