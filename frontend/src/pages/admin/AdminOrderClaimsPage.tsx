import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { AdminClaimCompleteModal } from '@/components/admin/AdminClaimCompleteModal';
import {
  AdminClaimStatusChips,
  type ClaimStatusFilter,
} from '@/components/admin/AdminClaimStatusChips';
import { AdminOrderClaimTable } from '@/components/admin/AdminOrderClaimTable';
import { AdminReasonModal } from '@/components/admin/AdminReasonModal';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { TableSkeleton } from '@/components/common/TableSkeleton';
import { useToast } from '@/components/common/toastContext';
import {
  useApproveAdminOrderClaim,
  useCollectAdminOrderClaim,
  useCompleteAdminOrderClaim,
  useRejectAdminOrderClaim,
} from '@/hooks/mutations/useAdminOrderMutations';
import { useAdminOrderClaimCounts } from '@/hooks/queries/useAdminOrderClaimCounts';
import { useAdminOrderClaims } from '@/hooks/queries/useAdminOrderClaims';
import { useFallbackPageRedirect } from '@/hooks/useFallbackPageRedirect';
import type { AdminOrderClaimSummary, OrderClaimType } from '@/types/adminOrder';
import {
  CLAIM_STATUSES_BY_TYPE,
  CLAIM_TYPE_DESCRIPTION,
  getClaimStatusLabel,
  countActionableClaims,
  pickClaimCounts,
  type AdminClaimAction,
} from '@/utils/adminOrderActions';
import { getErrorMessage } from '@/utils/apiError';
import { getFallbackPage } from '@/utils/pagination';

const CLAIM_PAGE_SIZE = 20;

const CLAIM_TABS: { type: OrderClaimType; label: string }[] = [
  { type: 'CANCEL', label: '취소' },
  { type: 'RETURN', label: '반품' },
];

const DEFAULT_STATUS: ClaimStatusFilter = 'REQUESTED';

const isClaimType = (value: string | null): value is OrderClaimType =>
  value === 'CANCEL' || value === 'RETURN';

/** 파라미터가 없거나 이 유형에 없는 상태면 처리 대기로 돌린다. */
const parseStatus = (value: string | null, type: OrderClaimType): ClaimStatusFilter => {
  if (value === 'ALL') {
    return 'ALL';
  }
  return CLAIM_STATUSES_BY_TYPE[type].find((status) => status === value) ?? DEFAULT_STATUS;
};

const parsePage = (value: string | null): number =>
  value !== null && /^\d+$/.test(value) ? Number(value) : 0;

const getEmptyTitle = (type: OrderClaimType, status: ClaimStatusFilter): string => {
  if (status === 'ALL') {
    return '클레임이 없습니다';
  }
  const label = getClaimStatusLabel(type, status);
  return status === 'REQUESTED' ? `처리 대기 중인 ${label}이 없습니다` : `${label} 내역이 없습니다`;
};

interface PendingAction {
  claim: AdminOrderClaimSummary;
  action: AdminClaimAction;
}

export default function AdminOrderClaimsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { showToast } = useToast();

  const typeParam = searchParams.get('type');
  const type: OrderClaimType = isClaimType(typeParam) ? typeParam : 'CANCEL';
  const status = parseStatus(searchParams.get('status'), type);
  const page = parsePage(searchParams.get('page'));

  const [pendingAction, setPendingAction] = useState<PendingAction>();

  const { data, isPending, isError, error, isPlaceholderData, refetch } = useAdminOrderClaims({
    type,
    status: status === 'ALL' ? undefined : status,
    page,
    size: CLAIM_PAGE_SIZE,
  });
  const fallbackPage =
    data && !isPlaceholderData
      ? getFallbackPage(page, data.content.length, data.totalPages)
      : undefined;
  const isMovingToFallbackPage = fallbackPage !== undefined;

  const { data: counts } = useAdminOrderClaimCounts();
  const typeCounts = counts && pickClaimCounts(counts, type);

  const approveMutation = useApproveAdminOrderClaim();
  const collectMutation = useCollectAdminOrderClaim();
  const completeMutation = useCompleteAdminOrderClaim();
  const rejectMutation = useRejectAdminOrderClaim();

  const updateSearch = (next: {
    type?: OrderClaimType;
    status?: ClaimStatusFilter;
    page?: number;
  }) => {
    const params = new URLSearchParams();
    params.set('type', next.type ?? type);
    const nextStatus = next.status ?? status;
    if (nextStatus !== DEFAULT_STATUS) {
      params.set('status', nextStatus);
    }
    if (next.page) {
      params.set('page', String(next.page));
    }
    setSearchParams(params);
  };

  useFallbackPageRedirect(fallbackPage);

  const closeAction = () => setPendingAction(undefined);

  const handleDone = (message: string) => {
    showToast('success', message);
    closeAction();
  };

  const handleFailed = (failure: unknown) => {
    showToast('error', getErrorMessage(failure));
    closeAction();
  };

  const handleApprove = (claim: AdminOrderClaimSummary) =>
    approveMutation.mutate(claim.claimId, {
      onSuccess: () => handleDone('취소 클레임을 승인했습니다.'),
      onError: handleFailed,
    });

  const handleCollect = (claim: AdminOrderClaimSummary) =>
    collectMutation.mutate(claim.claimId, {
      onSuccess: () => handleDone('수거를 시작했습니다.'),
      onError: handleFailed,
    });

  const handleComplete = (claim: AdminOrderClaimSummary, restock: boolean) =>
    completeMutation.mutate(
      { claimId: claim.claimId, payload: { restock } },
      { onSuccess: () => handleDone('반품 수거를 완료했습니다.'), onError: handleFailed },
    );

  const handleReject = (claim: AdminOrderClaimSummary, rejectReason: string) =>
    rejectMutation.mutate(
      { claimId: claim.claimId, payload: { rejectReason } },
      { onSuccess: () => handleDone('클레임을 거부했습니다.'), onError: handleFailed },
    );

  const emptyTitle = getEmptyTitle(type, status);

  const claim = pendingAction?.claim;
  const action = pendingAction?.action;

  return (
    <div>
      <div className="mb-4">
        <h2 className="text-[17px] font-bold tracking-tight">취소·반품 관리</h2>
        <p className="mt-1.5 text-sm text-content-muted">
          {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}건`}
        </p>
      </div>

      <div role="tablist" aria-label="클레임 종류" className="mb-2 flex gap-1">
        {CLAIM_TABS.map((tab) => {
          const pendingCount =
            counts && countActionableClaims(pickClaimCounts(counts, tab.type), tab.type);
          return (
            <button
              key={tab.type}
              type="button"
              role="tab"
              aria-selected={tab.type === type}
              onClick={() => updateSearch({ type: tab.type, status: DEFAULT_STATUS, page: 0 })}
              className={`flex h-9 items-center gap-1.5 rounded-md border px-4 text-sm font-medium ${
                tab.type === type
                  ? 'border-content bg-content text-surface'
                  : 'border-line-strong bg-surface text-content-muted hover:bg-surface-muted'
              }`}
            >
              {tab.label}
              {pendingCount !== undefined && pendingCount > 0 && (
                <span
                  aria-label={`처리할 클레임 ${pendingCount}건`}
                  className="rounded-full bg-accent px-1.5 text-xs leading-5 text-accent-content"
                >
                  {pendingCount}
                </span>
              )}
            </button>
          );
        })}
      </div>
      <p className="mb-4 text-sm text-content-muted">{CLAIM_TYPE_DESCRIPTION[type]}</p>

      <AdminClaimStatusChips
        type={type}
        value={status}
        counts={typeCounts}
        onChange={(next) => updateSearch({ status: next, page: 0 })}
      />

      {(isPending || isMovingToFallbackPage) && <TableSkeleton columns={8} />}

      {!isPending && !isMovingToFallbackPage && isError && (
        <QueryErrorState error={error} onRetry={refetch} title="클레임을 불러오지 못했습니다" />
      )}

      {!isPending && !isMovingToFallbackPage && !isError && data && data.content.length === 0 && (
        <EmptyState title={emptyTitle} />
      )}

      {!isPending && !isError && data && data.content.length > 0 && (
        <div className={isPlaceholderData ? 'opacity-60' : ''}>
          <AdminOrderClaimTable
            claims={data.content}
            onAction={(target, targetAction) =>
              setPendingAction({ claim: target, action: targetAction })
            }
          />

          <div className="mt-6">
            <Pagination
              page={page}
              totalPages={data.totalPages}
              onChange={(next) => updateSearch({ page: next })}
            />
          </div>
        </div>
      )}

      {claim && action === 'approve' && (
        <ConfirmDialog
          open
          onClose={closeAction}
          onConfirm={() => handleApprove(claim)}
          title="취소 승인"
          description={`${claim.productOrderNumber} 취소를 승인하고 환불합니다.`}
          confirmLabel="승인"
          variant="primary"
          pending={approveMutation.isPending}
        />
      )}

      {claim && action === 'collect' && (
        <ConfirmDialog
          open
          onClose={closeAction}
          onConfirm={() => handleCollect(claim)}
          title="수거 시작"
          description={`${claim.productOrderNumber} 반품 수거를 시작합니다.`}
          confirmLabel="수거 시작"
          variant="primary"
          pending={collectMutation.isPending}
        />
      )}

      {claim && action === 'complete' && (
        <AdminClaimCompleteModal
          productOrderNumber={claim.productOrderNumber}
          pending={completeMutation.isPending}
          onClose={closeAction}
          onSubmit={(restock) => handleComplete(claim, restock)}
        />
      )}

      {claim && action === 'reject' && (
        <AdminReasonModal
          title="클레임 거부"
          description={`${claim.productOrderNumber} 클레임을 거부합니다. 상품주문은 원래 단계로 돌아갑니다.`}
          label="거부 사유"
          submitLabel="거부"
          required
          pending={rejectMutation.isPending}
          onClose={closeAction}
          onSubmit={(reason) => handleReject(claim, reason)}
        />
      )}
    </div>
  );
}
