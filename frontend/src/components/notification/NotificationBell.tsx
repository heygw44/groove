import { Link } from 'react-router-dom';

import { useUnreadNotificationCount } from '@/hooks/queries/useUnreadNotificationCount';

const MAX_DISPLAY_COUNT = 99;

const formatBadgeCount = (count: number): string =>
  count > MAX_DISPLAY_COUNT ? `${MAX_DISPLAY_COUNT}+` : String(count);

export function NotificationBell() {
  const { data } = useUnreadNotificationCount();
  const count = data?.count ?? 0;

  return (
    <Link
      to="/notifications"
      aria-label={count > 0 ? `알림 ${count}개` : '알림'}
      className="relative inline-flex h-8 w-8 items-center justify-center rounded-md text-content-muted hover:bg-surface-muted hover:text-content"
    >
      <svg
        width="20"
        height="20"
        viewBox="0 0 20 20"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <path d="M5 8.5a5 5 0 0 1 10 0c0 3 1 4.2 1.5 4.8H3.5C4 12.7 5 11.5 5 8.5Z" />
        <path d="M8.3 15.8a1.8 1.8 0 0 0 3.4 0" />
      </svg>
      {count > 0 && (
        <span className="absolute -right-0.5 -top-0.5 rounded-full bg-accent px-1.5 text-[11px] text-accent-content">
          {formatBadgeCount(count)}
        </span>
      )}
    </Link>
  );
}
