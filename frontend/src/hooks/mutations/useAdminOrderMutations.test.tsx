import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { approveAdminOrderClaim } from '@/api/admin';
import { useApproveAdminOrderClaim } from '@/hooks/mutations/useAdminOrderMutations';

vi.mock('@/api/admin', () => ({
  approveAdminOrderClaim: vi.fn(),
  cancelAdminOrderItem: vi.fn(),
  collectAdminOrderClaim: vi.fn(),
  completeAdminOrderClaim: vi.fn(),
  confirmAdminOrderItems: vi.fn(),
  deliverAdminOrderItems: vi.fn(),
  rejectAdminOrderClaim: vi.fn(),
  shipAdminOrderItems: vi.fn(),
}));

describe('useApproveAdminOrderClaim', () => {
  it('승인이 실패해도 관련 쿼리를 무효화한다', async () => {
    // given
    vi.mocked(approveAdminOrderClaim).mockRejectedValue(new Error('환불 실패'));
    const queryClient = new QueryClient();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useApproveAdminOrderClaim(), { wrapper });

    // when
    result.current.mutate(1);

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(invalidate).toHaveBeenCalled();
  });
});
