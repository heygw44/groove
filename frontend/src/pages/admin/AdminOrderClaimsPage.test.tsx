import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import {
  useApproveAdminOrderClaim,
  useCollectAdminOrderClaim,
  useCompleteAdminOrderClaim,
  useRejectAdminOrderClaim,
} from '@/hooks/mutations/useAdminOrderMutations';
import { useAdminOrderClaims } from '@/hooks/queries/useAdminOrderClaims';
import AdminOrderClaimsPage from '@/pages/admin/AdminOrderClaimsPage';
import type { AdminOrderClaimSummary } from '@/types/adminOrder';

vi.mock('@/hooks/mutations/useAdminOrderMutations', () => ({
  useApproveAdminOrderClaim: vi.fn(),
  useCollectAdminOrderClaim: vi.fn(),
  useCompleteAdminOrderClaim: vi.fn(),
  useRejectAdminOrderClaim: vi.fn(),
}));

vi.mock('@/hooks/queries/useAdminOrderClaims', () => ({
  useAdminOrderClaims: vi.fn(),
}));

const buildClaim = (overrides: Partial<AdminOrderClaimSummary>): AdminOrderClaimSummary => ({
  claimId: 1,
  type: 'CANCEL',
  status: 'REQUESTED',
  productOrderNumber: 'ORD-1-01',
  orderNumber: 'ORD-1',
  memberEmail: 'member@groove.com',
  productName: '레코드 판',
  reason: '단순 변심',
  requestedAt: '2026-09-13T00:00:00',
  ...overrides,
});

const mockClaims = (content: AdminOrderClaimSummary[]) => {
  vi.mocked(useAdminOrderClaims).mockReturnValue({
    data: { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 },
    isPending: false,
    isError: false,
    error: null,
    isPlaceholderData: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useAdminOrderClaims>);
};

const mutationStub = <T,>() => ({ mutate: vi.fn(), isPending: false }) as unknown as T;

const renderPage = () =>
  render(
    <MemoryRouter>
      <ToastProvider>
        <AdminOrderClaimsPage />
      </ToastProvider>
    </MemoryRouter>,
  );

describe('AdminOrderClaimsPage', () => {
  const approve = mutationStub<ReturnType<typeof useApproveAdminOrderClaim>>();
  const collect = mutationStub<ReturnType<typeof useCollectAdminOrderClaim>>();
  const complete = mutationStub<ReturnType<typeof useCompleteAdminOrderClaim>>();
  const reject = mutationStub<ReturnType<typeof useRejectAdminOrderClaim>>();

  beforeEach(() => {
    vi.mocked(useApproveAdminOrderClaim).mockReturnValue(approve);
    vi.mocked(useCollectAdminOrderClaim).mockReturnValue(collect);
    vi.mocked(useCompleteAdminOrderClaim).mockReturnValue(complete);
    vi.mocked(useRejectAdminOrderClaim).mockReturnValue(reject);
  });

  afterEach(() => vi.clearAllMocks());

  it('접수된 취소 클레임에는 승인·거부만 보이고 승인하면 클레임 id 로 요청한다', async () => {
    // given
    const user = userEvent.setup();
    mockClaims([buildClaim({ claimId: 7 })]);
    renderPage();

    // when
    expect(screen.queryByRole('button', { name: /수거/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'ORD-1-01 승인' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '승인' }));

    // then
    expect(approve.mutate).toHaveBeenCalledWith(7, expect.any(Object));
  });

  it('수거중 반품 클레임은 재입고 여부를 담아 완료 요청한다', async () => {
    // given
    const user = userEvent.setup();
    mockClaims([buildClaim({ claimId: 9, type: 'RETURN', status: 'COLLECTING' })]);
    renderPage();

    // when
    await user.click(screen.getByRole('button', { name: 'ORD-1-01 완료' }));
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: '수거 완료' }));

    // then
    expect(complete.mutate).toHaveBeenCalledWith(
      { claimId: 9, payload: { restock: false } },
      expect.any(Object),
    );
  });

  it('거부는 사유를 입력해야 제출할 수 있다', async () => {
    // given
    const user = userEvent.setup();
    mockClaims([buildClaim({ claimId: 3, type: 'RETURN', status: 'REQUESTED' })]);
    renderPage();

    // when
    await user.click(screen.getByRole('button', { name: 'ORD-1-01 거부' }));
    const dialog = screen.getByRole('dialog');
    const submit = within(dialog).getByRole('button', { name: '거부' });
    expect(submit).toBeDisabled();
    await user.type(within(dialog).getByLabelText(/거부 사유/), '  상태 불량  ');
    await user.click(submit);

    // then
    expect(reject.mutate).toHaveBeenCalledWith(
      { claimId: 3, payload: { rejectReason: '상태 불량' } },
      expect.any(Object),
    );
  });

  it('종료된 클레임에는 처리 버튼이 없다', () => {
    // given
    mockClaims([buildClaim({ status: 'DONE' })]);

    // when
    renderPage();

    // then
    expect(screen.queryByRole('button', { name: /ORD-1-01/ })).not.toBeInTheDocument();
  });
});
