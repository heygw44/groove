import type { ReactNode } from 'react';
import { NavLink, Outlet } from 'react-router-dom';

interface AdminNavItem {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
}

const NAV_ITEMS: AdminNavItem[] = [
  {
    to: '/admin',
    label: '대시보드',
    end: true,
    icon: (
      <svg
        width="16"
        height="16"
        viewBox="0 0 20 20"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <rect x="3" y="3" width="6" height="6" rx="1" />
        <rect x="11" y="3" width="6" height="6" rx="1" />
        <rect x="3" y="11" width="6" height="6" rx="1" />
        <rect x="11" y="11" width="6" height="6" rx="1" />
      </svg>
    ),
  },
  {
    to: '/admin/products',
    label: '상품',
    icon: (
      <svg
        width="16"
        height="16"
        viewBox="0 0 20 20"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <circle cx="10" cy="10" r="7" />
        <circle cx="10" cy="10" r="2" />
      </svg>
    ),
  },
  {
    to: '/admin/orders',
    label: '주문',
    icon: (
      <svg
        width="16"
        height="16"
        viewBox="0 0 20 20"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <path d="M4 6h12l-1 9.5a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 5 15.5L4 6Z" />
        <path d="M7 6V5a3 3 0 0 1 6 0v1" />
      </svg>
    ),
  },
  {
    to: '/admin/members',
    label: '회원',
    icon: (
      <svg
        width="16"
        height="16"
        viewBox="0 0 20 20"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <circle cx="7" cy="7" r="2.6" />
        <path d="M2.5 16c0-2.4 2-3.8 4.5-3.8s4.5 1.4 4.5 3.8" />
        <circle cx="14.5" cy="7.5" r="2" />
        <path d="M12.8 12.6c1.9.2 3.7 1.5 3.7 3.4" />
      </svg>
    ),
  },
  {
    to: '/admin/coupons',
    label: '쿠폰',
    icon: (
      <svg
        width="16"
        height="16"
        viewBox="0 0 20 20"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <path d="M3 8.5V6.5a1.5 1.5 0 0 1 1.5-1.5h11A1.5 1.5 0 0 1 17 6.5v2a1.5 1.5 0 0 0 0 3v2a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 3 13.5v-2a1.5 1.5 0 0 0 0-3Z" />
        <path d="M8 5v10" strokeDasharray="1.6 1.6" />
      </svg>
    ),
  },
  {
    to: '/admin/limited-drops',
    label: '한정반',
    icon: (
      <svg
        width="16"
        height="16"
        viewBox="0 0 20 20"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <path d="M10 3.5v3" />
        <circle cx="10" cy="10" r="6.5" />
        <path d="M10 10l3.2 1.8" />
      </svg>
    ),
  },
  {
    to: '/admin/audit-logs',
    label: '감사 로그',
    icon: (
      <svg
        width="16"
        height="16"
        viewBox="0 0 20 20"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <path d="M5 3.5h7l3 3v10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-12a1 1 0 0 1 1-1Z" />
        <path d="M7 9.5h6M7 12.5h6M7 15h3" />
      </svg>
    ),
  },
];

export function AdminLayout() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <h1 className="mb-6 text-2xl font-bold tracking-tight">관리자</h1>

      <div className="grid gap-6 md:grid-cols-[196px_minmax(0,1fr)] md:gap-11">
        <nav className="flex gap-1 overflow-x-auto md:flex-col md:overflow-visible">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `flex h-9 items-center gap-2 rounded-md px-3 text-sm whitespace-nowrap ${
                  isActive
                    ? 'bg-accent-soft font-bold text-accent-hover'
                    : 'text-content-muted hover:bg-surface hover:text-content'
                }`
              }
            >
              {item.icon}
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="flex flex-col gap-5">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
