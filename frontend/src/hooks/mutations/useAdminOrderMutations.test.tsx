import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';

import {
  approveAdminOrderClaim,
  cancelAdminOrderItem,
  collectAdminOrderClaim,
  completeAdminOrderClaim,
  confirmAdminOrderItems,
  rejectAdminOrderClaim,
} from '@/api/admin';
import {
  useApproveAdminOrderClaim,
  useCancelAdminOrderItem,
  useCollectAdminOrderClaim,
  useCompleteAdminOrderClaim,
  useConfirmAdminOrderItems,
  useRejectAdminOrderClaim,
} from '@/hooks/mutations/useAdminOrderMutations';
import { adminProductKeys } from '@/hooks/queries/queryKeys';

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

describe('관리자 상품 목록 무효화', () => {
  const run = async (useMutationHook: () => { mutate: (v: never) => void; isSuccess: boolean }) => {
    const queryClient = new QueryClient();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(useMutationHook, { wrapper });
    result.current.mutate(undefined as never);
    await waitFor(() => expect(invalidate).toHaveBeenCalled());
    await waitFor(() => expect(invalidate.mock.calls.length).toBeGreaterThanOrEqual(4));
    return invalidate.mock.calls.map(([filters]) => filters?.queryKey);
  };

  it.each([
    ['판매취소', useCancelAdminOrderItem, cancelAdminOrderItem],
    ['취소 승인', useApproveAdminOrderClaim, approveAdminOrderClaim],
    ['반품 완료', useCompleteAdminOrderClaim, completeAdminOrderClaim],
  ])('%s 는 재고가 바뀌므로 관리자 상품 쿼리도 무효화한다', async (_name, hook, api) => {
    // given
    vi.mocked(api as never as typeof approveAdminOrderClaim).mockResolvedValue(undefined as never);

    // when
    const keys = await run(hook as never);

    // then
    expect(keys).toContainEqual(adminProductKeys.all);
  });

  it.each([
    ['클레임 거부', useRejectAdminOrderClaim, rejectAdminOrderClaim],
    ['수거 시작', useCollectAdminOrderClaim, collectAdminOrderClaim],
    ['발주확인', useConfirmAdminOrderItems, confirmAdminOrderItems],
  ])('%s 는 관리자 상품 쿼리를 무효화하지 않는다', async (_name, hook, api) => {
    // given
    vi.mocked(api as never as typeof approveAdminOrderClaim).mockResolvedValue(undefined as never);

    // when
    const keys = await run(hook as never);

    // then
    expect(keys).not.toContainEqual(adminProductKeys.all);
  });
});
