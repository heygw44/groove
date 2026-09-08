import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ReconcileAlertBanner } from '@/components/admin/dashboard/ReconcileAlertBanner';
import { adminStatsKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { ReconcileLog } from '@/types/adminStats';
import type { PageResponse } from '@/types/api';

vi.mock('@/api/admin', () => ({
  getAdminReconcileLogs: vi.fn(),
}));

const buildPage = (totalElements: number): PageResponse<ReconcileLog> => ({
  content: [],
  page: 0,
  size: 1,
  totalElements,
  totalPages: totalElements > 0 ? 1 : 0,
});

const renderBanner = (totalElements?: number) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  if (totalElements !== undefined) {
    queryClient.setQueryData(
      adminStatsKeys.reconcileLogs({ repaired: false, page: 0, size: 1 }),
      buildPage(totalElements),
    );
  }

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ReconcileAlertBanner />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

afterEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
});

describe('ReconcileAlertBanner', () => {
  it('미해결 건수가 0건이면 아무것도 렌더하지 않는다', () => {
    // given
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    // when
    const { container } = renderBanner(0);

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it('미해결 건수가 있으면 경고 배너에 건수를 표시한다', () => {
    // given
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    // when
    renderBanner(3);

    // then
    expect(screen.getByText('집계 불일치 3건이 자동 복구되지 않았습니다.')).toBeInTheDocument();
  });

  it('로딩 중이면 아무것도 렌더하지 않는다', () => {
    // given
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    // when
    const { container } = renderBanner();

    // then
    expect(container).toBeEmptyDOMElement();
  });
});
