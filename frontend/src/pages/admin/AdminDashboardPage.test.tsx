import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  useAdminDailySales,
  useAdminPopularProducts,
  useAdminStatsSummary,
} from '@/hooks/queries/useAdminStats';
import AdminDashboardPage from '@/pages/admin/AdminDashboardPage';
import type { AdminStatsSummary } from '@/types/adminStats';

vi.mock('@/hooks/queries/useAdminStats', () => ({
  useAdminStatsSummary: vi.fn(),
  useAdminDailySales: vi.fn(),
  useAdminPopularProducts: vi.fn(),
}));

vi.mock('@/components/admin/dashboard/ReconcileAlertBanner', () => ({
  ReconcileAlertBanner: () => null,
}));
vi.mock('@/components/admin/dashboard/LimitedDropStatsSection', () => ({
  LimitedDropStatsSection: () => null,
}));
vi.mock('@/components/admin/dashboard/ReconcileLogSection', () => ({
  ReconcileLogSection: () => null,
}));

const SUMMARY: AdminStatsSummary = {
  todaySalesAmount: 0,
  todayCancelAmount: 0,
  todayOrderCount: 0,
  todayNewMemberCount: 0,
  newOrderCount: 0,
  depositWaitingCount: 0,
  cancelRequestCount: 1,
  returnRequestCount: 3,
};

const pendingQuery = { isPending: true, isError: false, data: undefined };

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    vi.mocked(useAdminStatsSummary).mockReturnValue({
      isPending: false,
      isError: false,
      data: SUMMARY,
    } as unknown as ReturnType<typeof useAdminStatsSummary>);
    vi.mocked(useAdminDailySales).mockReturnValue(
      pendingQuery as unknown as ReturnType<typeof useAdminDailySales>,
    );
    vi.mocked(useAdminPopularProducts).mockReturnValue(
      pendingQuery as unknown as ReturnType<typeof useAdminPopularProducts>,
    );
  });

  describe('반품 카드', () => {
    it('반품 처리 대기 라벨로 반품 전체 목록에 연결한다', () => {
      // given & when
      renderPage();

      // then
      const link = screen.getByRole('link', { name: /반품 처리 대기/ });
      expect(link).toHaveAttribute('href', '/admin/order-claims?type=RETURN&status=ALL');
      expect(link).toHaveTextContent('3건');
    });
  });
});
