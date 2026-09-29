import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { AdminClaimCompleteModal } from '@/components/admin/AdminClaimCompleteModal';
import { AdminOrderClaimTable } from '@/components/admin/AdminOrderClaimTable';
import { AdminReasonModal } from '@/components/admin/AdminReasonModal';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { Select } from '@/components/common/Select';
import { TableSkeleton } from '@/components/common/TableSkeleton';
import { useToast } from '@/components/common/toastContext';
import {
  useApproveAdminOrderClaim,
  useCollectAdminOrderClaim,
  useCompleteAdminOrderClaim,
  useRejectAdminOrderClaim,
} from '@/hooks/mutations/useAdminOrderMutations';
import { useAdminOrderClaims } from '@/hooks/queries/useAdminOrderClaims';
import type { AdminOrderClaimSummary, OrderClaimStatus, OrderClaimType } from '@/types/adminOrder';
import { CLAIM_STATUS_LABEL, type AdminClaimAction } from '@/utils/adminOrderActions';
import { getErrorMessage } from '@/utils/apiError';

const CLAIM_PAGE_SIZE = 20;

const CLAIM_TABS: { type: OrderClaimType; label: string }[] = [
  { type: 'CANCEL', label: '취소' },
  { type: 'RETURN', label: '반품' },
];

const CLAIM_STATUSES = Object.keys(CLAIM_STATUS_LABEL) as OrderClaimStatus[];

const isClaimType = (value: string | null): value is OrderClaimType =>
  value === 'CANCEL' || value === 'RETURN';

const isClaimStatus = (value: string | null): value is OrderClaimStatus =>
  CLAIM_STATUSES.some((status) => status === value);

const parsePage = (value: string | null): number =>
  value !== null && /^\d+$/.test(value) ? Number(value) : 0;

interface PendingAction {
  claim: AdminOrderClaimSummary;
  action: AdminClaimAction;
}

export default function AdminOrderClaimsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { showToast } = useToast();

  const typeParam = searchParams.get('type');
  const statusParam = searchParams.get('status');
  const type: OrderClaimType = isClaimType(typeParam) ? typeParam : 'CANCEL';
  const status = isClaimStatus(statusParam) ? statusParam : undefined;
  const page = parsePage(searchParams.get('page'));

  const [pendingAction, setPendingAction] = useState<PendingAction>();

  const { data, isPending, isError, error, isPlaceholderData, refetch } = useAdminOrderClaims({
    type,
    status,
    page,
    size: CLAIM_PAGE_SIZE,
  });

  const approveMutation = useApproveAdminOrderClaim();
  const collectMutation = useCollectAdminOrderClaim();
  const completeMutation = useCompleteAdminOrderClaim();
  const rejectMutation = useRejectAdminOrderClaim();

  const updateSearch = (next: {
    type?: OrderClaimType;
    status?: OrderClaimStatus;
    page?: number;
  }) => {
    const params = new URLSearchParams();
    params.set('type', next.type ?? type);
    const nextStatus = 'status' in next ? next.status : status;
    if (nextStatus !== undefined) {
      params.set('status', nextStatus);
    }
    if (next.page) {
      params.set('page', String(next.page));
    }
    setSearchParams(params);
  };

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

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div role="tablist" aria-label="클레임 종류" className="flex gap-1">
          {CLAIM_TABS.map((tab) => (
            <button
              key={tab.type}
              type="button"
              role="tab"
              aria-selected={tab.type === type}
              onClick={() => updateSearch({ type: tab.type, page: 0 })}
              className={`h-9 rounded-md px-4 text-sm font-medium ${
                tab.type === type
                  ? 'bg-content text-surface'
                  : 'text-content-muted hover:bg-surface-muted'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <Select
          aria-label="클레임 상태 필터"
          value={status ?? ''}
          onChange={(event) =>
            updateSearch({
              status: isClaimStatus(event.target.value) ? event.target.value : undefined,
              page: 0,
            })
          }
          className="w-32"
        >
          <option value="">전체</option>
          {CLAIM_STATUSES.map((claimStatus) => (
            <option key={claimStatus} value={claimStatus}>
              {CLAIM_STATUS_LABEL[claimStatus]}
            </option>
          ))}
        </Select>
      </div>

      {isPending && <TableSkeleton columns={8} />}

      {!isPending && isError && (
        <QueryErrorState error={error} onRetry={refetch} title="클레임을 불러오지 못했습니다" />
      )}

      {!isPending && !isError && data && data.content.length === 0 && (
        <EmptyState title="조건에 맞는 클레임이 없습니다" />
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
