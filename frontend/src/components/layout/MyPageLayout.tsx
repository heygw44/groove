import type { ReactNode } from 'react';
import { Suspense } from 'react';
import { NavLink, Outlet } from 'react-router-dom';

import { PageContainer } from '@/components/common/PageContainer';
import { RouteFallback } from '@/components/common/RouteFallback';

interface MyPageNavItem {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
}

const NAV_ITEMS: MyPageNavItem[] = [
  {
    to: '/mypage',
    label: '내 정보',
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
        <circle cx="10" cy="7" r="3.2" />
        <path d="M4 16.5c0-2.6 2.7-4.2 6-4.2s6 1.6 6 4.2" />
      </svg>
    ),
  },
  {
    to: '/notifications',
    label: '알림',
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
        <path d="M5 8.5a5 5 0 0 1 10 0c0 3 1 4.2 1.5 4.8H3.5C4 12.7 5 11.5 5 8.5Z" />
        <path d="M8.3 15.8a1.8 1.8 0 0 0 3.4 0" />
      </svg>
    ),
  },
  {
    to: '/orders',
    label: '주문 내역',
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
        <path d="M4.5 6.5h11l-.8 9a1.5 1.5 0 0 1-1.5 1.4H6.8a1.5 1.5 0 0 1-1.5-1.4l-.8-9Z" />
        <path d="M7.2 6.5V5a2.8 2.8 0 1 1 5.6 0v1.5" />
      </svg>
    ),
  },
  {
    to: '/mypage/wishlist',
    label: '찜 목록',
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
        <path d="M10 17s-6.2-3.8-8.3-7.7C.5 6.7 1.8 3.8 4.6 3.1c1.7-.4 3.4.3 4.4 1.7.3.4.6.9.8 1.4.2-.5.5-1 .8-1.4 1-1.4 2.7-2.1 4.4-1.7 2.8.7 4.1 3.6 2.9 5.9-2.1 3.9-8.3 7.7-8.3 7.7Z" />
      </svg>
    ),
  },
  {
    to: '/mypage/coupons',
    label: '쿠폰함',
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
        <path d="M2.5 8.2a1.7 1.7 0 0 0 0 3.6v2.7a1 1 0 0 0 1 1h13a1 1 0 0 0 1-1v-2.7a1.7 1.7 0 0 1 0-3.6V5.5a1 1 0 0 0-1-1h-13a1 1 0 0 0-1 1v2.7Z" />
        <path d="M8 4.8v10.4" strokeDasharray="1.8 1.8" />
      </svg>
    ),
  },
  {
    to: '/mypage/taste',
    label: '취향',
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
        <path d="M10 6.3l1 2.1 2.3.3-1.7 1.6.4 2.3-2-1.1-2 1.1.4-2.3-1.7-1.6 2.3-.3 1-2.1Z" />
      </svg>
    ),
  },
  {
    to: '/mypage/recent',
    label: '최근 본 상품',
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
        <circle cx="10" cy="10" r="7.5" />
        <path d="M10 6v4.2l3 1.8" />
      </svg>
    ),
  },
  {
    to: '/mypage/addresses',
    label: '배송지 관리',
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
        <path d="M10 17.5s5.5-4.6 5.5-9a5.5 5.5 0 1 0-11 0c0 4.4 5.5 9 5.5 9Z" />
        <circle cx="10" cy="8.4" r="2" />
      </svg>
    ),
  },
];

export function MyPageLayout() {
  return (
    <PageContainer size="md">
      <h1 className="mb-6 text-2xl font-bold tracking-tight">마이페이지</h1>

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

        <div className="flex min-w-0 flex-col gap-5">
          <Suspense fallback={<RouteFallback />}>
            <Outlet />
          </Suspense>
        </div>
      </div>
    </PageContainer>
  );
}
