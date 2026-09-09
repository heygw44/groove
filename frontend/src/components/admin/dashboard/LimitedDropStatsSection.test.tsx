import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LimitedDropStatsSection } from '@/components/admin/dashboard/LimitedDropStatsSection';
import { adminStatsKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { LimitedDropStats } from '@/types/adminStats';
import type { PageResponse } from '@/types/api';

vi.mock('@/api/admin', () => ({
  getAdminLimitedDropStats: vi.fn(),
}));

const PAGE_SIZE = 20;

const buildItem = (dropId: number): LimitedDropStats => ({
  dropId,
  productTitle: `한정반 ${dropId}`,
  status: 'SOLD_OUT',
  totalQuantity: 100,
  soldQuantity: 100,
  sellRate: 100,
  openAt: '2026-09-05T20:00:00',
  closeAt: '2026-09-05T20:30:00',
});

const buildPage = (totalElements: number, page = 0): PageResponse<LimitedDropStats> => {
  const totalPages = Math.ceil(totalElements / PAGE_SIZE);
  const start = page * PAGE_SIZE;
  const content = Array.from({ length: Math.min(PAGE_SIZE, totalElements - start) }, (_, i) =>
    buildItem(start + i + 1),
  );

  return { content, page, size: PAGE_SIZE, totalElements, totalPages };
};

const renderSection = (page: PageResponse<LimitedDropStats>) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  queryClient.setQueryData(adminStatsKeys.limitedDrops({ page: 0, size: PAGE_SIZE }), page);

  return render(
    <QueryClientProvider client={queryClient}>
      <LimitedDropStatsSection />
    </QueryClientProvider>,
  );
};

afterEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
});

describe('LimitedDropStatsSection', () => {
  it('목록이 비어 있으면 빈 상태를 보여주고 페이저는 없다', () => {
    // given
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    // when
    renderSection(buildPage(0));

    // then
    expect(screen.getByText('등록된 한정반이 없습니다')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: '페이지' })).not.toBeInTheDocument();
  });

  it('페이지가 여러 개면 페이저가 나타난다', () => {
    // given
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    // when
    renderSection(buildPage(45));

    // then
    expect(screen.getByRole('navigation', { name: '페이지' })).toBeInTheDocument();
  });

  it('페이지가 하나뿐이면 페이저가 없다', () => {
    // given
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    // when
    renderSection(buildPage(5));

    // then
    expect(screen.queryByRole('navigation', { name: '페이지' })).not.toBeInTheDocument();
  });
});
