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
  onDelete: (id: number) => void;
}

export function NotificationRow({ item, onRead, onDelete }: NotificationRowProps) {
  const unread = isUnreadNotification(item);
  const link = buildNotificationLink(item);
  const message = buildNotificationMessage(item);

  const handleActivate = () => {
    if (unread) {
      onRead(item.id);
    }
  };

  const text: ReactNode = (
    <>
      <p className={`text-sm ${unread ? 'font-medium text-content' : 'text-content-muted'}`}>
        {unread && <span className="sr-only">읽지 않음. </span>}
        {message}
      </p>
      <p className="mt-1 text-xs text-content-subtle">{formatDateTime(item.createdAt)}</p>
    </>
  );

  // 이동 또는 읽음 처리를 맡는 요소가 삭제 버튼까지 덮으면 링크 안에 버튼이 중첩된다.
  // stretched link 로 행 전체를 덮되, 삭제 버튼은 형제로 두고 z-10 으로 위에 올린다.
  const stretchedLinkClass = "block before:absolute before:inset-0 before:content-['']";

  return (
    <div
      className={`relative flex items-start gap-3 rounded-md px-1 py-3 ${
        unread ? 'bg-accent-soft/40' : ''
      }`}
    >
      <span
        aria-hidden
        className={`mt-1.5 h-2 w-2 flex-shrink-0 rounded-full ${
          unread ? 'bg-accent' : 'bg-transparent'
        }`}
      />
      <div className="min-w-0 flex-1">
        {link ? (
          <Link to={link} onClick={handleActivate} className={stretchedLinkClass}>
            {text}
          </Link>
        ) : unread ? (
          <button
            type="button"
            onClick={handleActivate}
            className={`${stretchedLinkClass} text-left`}
          >
            {text}
          </button>
        ) : (
          text
        )}
      </div>
      <button
        type="button"
        onClick={() => onDelete(item.id)}
        aria-label={`${message} 알림 삭제`}
        className="relative z-10 flex-shrink-0 rounded-md p-1 text-content-subtle transition-colors hover:bg-surface-muted hover:text-danger"
      >
        <svg
          width="15"
          height="15"
          viewBox="0 0 20 20"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          aria-hidden
        >
          <path d="M4.5 5.5h11" />
          <path d="M8 5.5V4a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v1.5" />
          <path d="M6 5.5 6.6 16a1 1 0 0 0 1 .95h4.8a1 1 0 0 0 1-.95l.6-10.5" />
        </svg>
      </button>
    </div>
  );
}
