import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ReconcileLogSection } from '@/components/admin/dashboard/ReconcileLogSection';
import { RECONCILE_LOGS_SECTION_ID } from '@/constants/adminReconcile';
import { adminStatsKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { ReconcileLog } from '@/types/adminStats';
import type { PageResponse } from '@/types/api';

vi.mock('@/api/admin', () => ({
  getAdminReconcileLogs: vi.fn(),
}));

const emptyPage: PageResponse<ReconcileLog> = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

const originalScrollIntoView = Element.prototype.scrollIntoView;
const scrollIntoView = vi.fn();

const renderSection = (initialEntry: string) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  queryClient.setQueryData(
    adminStatsKeys.reconcileLogs({ repaired: false, page: 0, size: 20 }),
    emptyPage,
  );

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <ReconcileLogSection />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

beforeEach(() => {
  Element.prototype.scrollIntoView = scrollIntoView;
});

afterEach(() => {
  vi.clearAllMocks();
  Element.prototype.scrollIntoView = originalScrollIntoView;
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
});

describe('ReconcileLogSection', () => {
  it('대사 로그 해시로 진입하면 섹션을 화면 위쪽으로 스크롤한다', () => {
    // given
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    // when
    renderSection(`/admin#${RECONCILE_LOGS_SECTION_ID}`);

    // then
    expect(scrollIntoView).toHaveBeenCalledTimes(1);
    expect(scrollIntoView).toHaveBeenCalledWith({ block: 'start' });
    expect(scrollIntoView.mock.contexts[0]).toBe(
      document.getElementById(RECONCILE_LOGS_SECTION_ID),
    );
  });

  it('해시 없이 진입하면 스크롤하지 않는다', () => {
    // given
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    // when
    renderSection('/admin');

    // then
    expect(scrollIntoView).not.toHaveBeenCalled();
  });
});
