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
import { useAdminOrderClaimCounts } from '@/hooks/queries/useAdminOrderClaimCounts';
import { useAdminOrderClaims } from '@/hooks/queries/useAdminOrderClaims';
import AdminOrderClaimsPage from '@/pages/admin/AdminOrderClaimsPage';
import type { AdminOrderClaimCounts, AdminOrderClaimSummary } from '@/types/adminOrder';

vi.mock('@/hooks/mutations/useAdminOrderMutations', () => ({
  useApproveAdminOrderClaim: vi.fn(),
  useCollectAdminOrderClaim: vi.fn(),
  useCompleteAdminOrderClaim: vi.fn(),
  useRejectAdminOrderClaim: vi.fn(),
}));

vi.mock('@/hooks/queries/useAdminOrderClaims', () => ({
  useAdminOrderClaims: vi.fn(),
}));

vi.mock('@/hooks/queries/useAdminOrderClaimCounts', () => ({
  useAdminOrderClaimCounts: vi.fn(),
}));

const COUNTS: AdminOrderClaimCounts = {
  cancel: { requested: 2, collecting: 0, done: 5, rejected: 0, withdrawn: 1, total: 8 },
  returns: { requested: 0, collecting: 2, done: 3, rejected: 0, withdrawn: 0, total: 5 },
};

const mockCounts = (counts?: AdminOrderClaimCounts) => {
  vi.mocked(useAdminOrderClaimCounts).mockReturnValue({
    data: counts,
    isError: counts === undefined,
  } as unknown as ReturnType<typeof useAdminOrderClaimCounts>);
};

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

const renderPage = (url = '/admin/order-claims') =>
  render(
    <MemoryRouter initialEntries={[url]}>
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
    mockCounts(COUNTS);
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

  describe('상태 칩', () => {
    const lastListParams = () => vi.mocked(useAdminOrderClaims).mock.lastCall?.[0];

    it('첫 진입하면 처리 대기(REQUESTED) 목록을 조회한다', () => {
      // given
      mockClaims([buildClaim({})]);

      // when
      renderPage();

      // then
      expect(lastListParams()).toMatchObject({ type: 'CANCEL', status: 'REQUESTED' });
      expect(screen.getByRole('button', { name: '취소요청 2' })).toHaveAttribute(
        'aria-pressed',
        'true',
      );
    });

    it('칩을 누르면 해당 상태로 조회하고 전체 칩이면 상태 없이 조회한다', async () => {
      // given
      const user = userEvent.setup();
      mockClaims([buildClaim({})]);
      renderPage();

      // when
      await user.click(screen.getByRole('button', { name: '취소완료 5' }));

      // then
      expect(lastListParams()).toMatchObject({ status: 'DONE' });

      // when
      await user.click(screen.getByRole('button', { name: '전체 8' }));

      // then
      expect(lastListParams()?.status).toBeUndefined();
    });

    it('취소 탭에는 수거중 칩이 없고 반품 탭에는 있다', async () => {
      // given
      const user = userEvent.setup();
      mockClaims([buildClaim({})]);
      renderPage();

      // when & then
      expect(screen.queryByRole('button', { name: /^수거중/ })).not.toBeInTheDocument();
      await user.click(screen.getByRole('tab', { name: /반품/ }));
      expect(screen.getByRole('button', { name: '수거중 2' })).toBeInTheDocument();
      expect(lastListParams()).toMatchObject({ type: 'RETURN', status: 'REQUESTED' });
    });

    it('취소 유형에 없는 상태 파라미터는 처리 대기로 돌린다', () => {
      // given
      mockClaims([buildClaim({})]);

      // when
      renderPage('/admin/order-claims?type=CANCEL&status=COLLECTING');

      // then
      expect(lastListParams()).toMatchObject({ status: 'REQUESTED' });
    });

    it('유형 탭 뱃지에 처리할 건수를 보이고 반품은 수거중까지 센다', () => {
      // given
      mockClaims([buildClaim({})]);

      // when
      renderPage();

      // then
      expect(screen.getByRole('tab', { name: /취소/ })).toHaveTextContent('2');
      expect(screen.getByRole('tab', { name: /반품/ })).toHaveTextContent('2');
    });

    it('처리할 건이 0건이면 유형 탭 뱃지를 숨긴다', () => {
      // given
      mockClaims([buildClaim({})]);
      mockCounts({
        ...COUNTS,
        returns: { requested: 0, collecting: 0, done: 3, rejected: 0, withdrawn: 0, total: 3 },
      });

      // when
      renderPage();

      // then
      expect(screen.getByRole('tab', { name: /반품/ })).not.toHaveTextContent(/\d/);
    });

    it('선택한 유형의 설명 문구를 보여준다', () => {
      // given
      mockClaims([buildClaim({})]);

      // when
      renderPage('/admin/order-claims?type=RETURN');

      // then
      expect(screen.getByText(/배송완료 후 7일 안에 들어온 반품요청/)).toBeInTheDocument();
    });

    it('건수 조회가 실패해도 라벨만 보이고 목록은 나온다', () => {
      // given
      mockCounts(undefined);
      mockClaims([buildClaim({})]);

      // when
      renderPage();

      // then
      expect(screen.getByRole('button', { name: '취소요청' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '전체' })).toBeInTheDocument();
      expect(screen.getByText('ORD-1-01')).toBeInTheDocument();
    });

    it('목록이 비면 선택한 칩에 맞는 안내를 보여준다', () => {
      // given
      mockClaims([]);

      // when
      renderPage();

      // then
      expect(screen.getByText('처리 대기 중인 취소요청이 없습니다')).toBeInTheDocument();
    });
  });
});
