import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { NotificationBell } from '@/components/notification/NotificationBell';
import { notificationKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/notification', () => ({
  getUnreadNotificationCount: vi.fn(),
}));

const renderBell = (unreadCount?: number) => {
  // staleTime: Infinity 로 캐시가 신선하다고 취급해 배경 refetch(목 함수가 undefined 를 반환)가
  // 렌더 직후 값을 덮어쓰지 않게 한다.
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  if (unreadCount !== undefined) {
    queryClient.setQueryData(notificationKeys.unreadCount, { count: unreadCount });
  }

  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <NotificationBell />
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
};

afterEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
});

describe('NotificationBell', () => {
  it('비로그인 상태면 안 읽은 개수를 조회하지 않는다', async () => {
    // given
    const { getUnreadNotificationCount } = await import('@/api/notification');
    useAuthStore.setState({ accessToken: null, isBootstrapping: false });

    // when
    renderBell();
    await new Promise((resolve) => setTimeout(resolve, 0));

    // then
    expect(getUnreadNotificationCount).not.toHaveBeenCalled();
    expect(screen.getByRole('link', { name: '알림' })).toBeInTheDocument();
  });

  it('안 읽은 개수가 있으면 뱃지와 라벨에 표시한다', () => {
    // given
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    // when
    renderBell(5);

    // then
    expect(screen.getByRole('link', { name: '알림 5개' })).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('개수가 0 이면 뱃지를 보여주지 않는다', () => {
    // given
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    // when
    renderBell(0);

    // then
    expect(screen.getByRole('link', { name: '알림' })).toBeInTheDocument();
  });

  it('개수가 100 이상이면 99+ 로 표시한다', () => {
    // given
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    // when
    renderBell(120);

    // then
    expect(screen.getByText('99+')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '알림 120개' })).toBeInTheDocument();
  });
});
