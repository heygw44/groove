import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

import type { NotificationItem } from '@/types/notification';
import { formatDateTime } from '@/utils/formatDate';
import {
  buildNotificationLink,
  buildNotificationMessage,
  isUnreadNotification,
} from '@/utils/notification';

interface NotificationRowProps {
  item: NotificationItem;
  onRead: (id: number) => void;
}

export function NotificationRow({ item, onRead }: NotificationRowProps) {
  const unread = isUnreadNotification(item);
  const link = buildNotificationLink(item);

  const handleClick = () => {
    if (unread) {
      onRead(item.id);
    }
  };

  const content: ReactNode = (
    <div className="flex items-start gap-3 px-1 py-3">
      <span
        aria-hidden
        className={`mt-1.5 h-2 w-2 flex-shrink-0 rounded-full ${
          unread ? 'bg-accent' : 'bg-transparent'
        }`}
      />
      <div className="min-w-0 flex-1">
        <p className={`text-sm ${unread ? 'font-medium text-content' : 'text-content-muted'}`}>
          {buildNotificationMessage(item)}
        </p>
        <p className="mt-1 text-xs text-content-subtle">{formatDateTime(item.createdAt)}</p>
      </div>
    </div>
  );

  const rowClassName = `block rounded-md ${unread ? 'bg-accent-soft/40' : ''}`;

  if (link) {
    return (
      <Link to={link} onClick={handleClick} className={rowClassName}>
        {content}
      </Link>
    );
  }

  // 이동할 곳이 없는 알림. 읽음 처리라도 할 게 남았을 때만 누를 수 있어야 한다.
  if (unread) {
    return (
      <button type="button" onClick={handleClick} className={`${rowClassName} w-full text-left`}>
        {content}
      </button>
    );
  }

  return <div className={rowClassName}>{content}</div>;
}
