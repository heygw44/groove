import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { AdminLimitedDropDetailDrawer } from '@/components/admin/AdminLimitedDropDetailDrawer';
import { AdminLimitedDropFormModal } from '@/components/admin/AdminLimitedDropFormModal';
import { AdminLimitedDropTable } from '@/components/admin/AdminLimitedDropTable';
import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { Select } from '@/components/common/Select';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import {
  useCloseAdminLimitedDrop,
  useOpenAdminLimitedDrop,
} from '@/hooks/mutations/useAdminLimitedDropMutations';
import { useAdminLimitedDrops } from '@/hooks/queries/useAdminLimitedDrops';
import type { AdminLimitedDropSummary, LimitedDropStatus } from '@/types/limitedDrop';
import { getErrorMessage } from '@/utils/apiError';

const STATUS_OPTIONS: { value: LimitedDropStatus | ''; label: string }[] = [
  { value: '', label: '전체' },
  { value: 'SCHEDULED', label: '예정' },
  { value: 'OPEN', label: '진행중' },
  { value: 'SOLD_OUT', label: '매진' },
  { value: 'CLOSED', label: '마감' },
];

const STATUS_VALUES = new Set<string>(
  STATUS_OPTIONS.map((option) => option.value).filter((value): value is LimitedDropStatus =>
    Boolean(value),
  ),
);

const PAGE_SIZE = 20;

const parseStatus = (value: string | null): LimitedDropStatus | undefined =>
  value && STATUS_VALUES.has(value) ? (value as LimitedDropStatus) : undefined;

const parsePage = (value: string | null): number => {
  if (value === null || !/^\d+$/.test(value)) {
    return 0;
  }
  return Number(value);
};

type PendingAction = { type: 'open' | 'close'; drop: AdminLimitedDropSummary };

export default function AdminLimitedDropsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseStatus(searchParams.get('status'));
  const page = parsePage(searchParams.get('page'));

  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<AdminLimitedDropSummary | undefined>(undefined);
  const [selectedId, setSelectedId] = useState<number | undefined>(undefined);
  const [pendingAction, setPendingAction] = useState<PendingAction | undefined>(undefined);

  const { showToast } = useToast();
  const { data, isPending, isError, isPlaceholderData, refetch } = useAdminLimitedDrops({
    status,
    page,
    size: PAGE_SIZE,
  });
  const openMutation = useOpenAdminLimitedDrop();
  const closeMutation = useCloseAdminLimitedDrop();

  const updateStatus = (next: LimitedDropStatus | '') => {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev);
      if (next) {
        params.set('status', next);
      } else {
        params.delete('status');
      }
      params.delete('page');
      return params;
    });
  };

  const updatePage = (nextPage: number) => {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev);
      if (nextPage > 0) {
        params.set('page', String(nextPage));
      } else {
        params.delete('page');
      }
      return params;
    });
  };

  const pendingMutation = pendingAction?.type === 'open' ? openMutation : closeMutation;

  const handleConfirmAction = () => {
    if (!pendingAction) {
      return;
    }
    const { type, drop } = pendingAction;
    const mutation = type === 'open' ? openMutation : closeMutation;

    mutation.mutate(drop.id, {
      onSuccess: () => {
        showToast('success', type === 'open' ? '드롭을 오픈했습니다.' : '드롭을 마감했습니다.');
        setPendingAction(undefined);
      },
      onError: (error) => {
        setPendingAction(undefined);
        showToast('error', getErrorMessage(error));
      },
    });
  };

  return (
    <div>
      <div className="mb-4 flex items-end justify-between gap-6">
        <div>
          <h2 className="text-[17px] font-bold tracking-tight">한정반 관리</h2>
          <p className="mt-1.5 text-sm text-content-muted">
            {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}개`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Select
            aria-label="상태 필터"
            value={status ?? ''}
            onChange={(event) => updateStatus(event.target.value as LimitedDropStatus | '')}
            className="w-32"
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
          <Button onClick={() => setCreating(true)}>드롭 등록</Button>
        </div>
      </div>

      {isPending && (
        <div className="flex min-h-48 items-center justify-center">
          <Spinner />
        </div>
      )}

      {!isPending && isError && (
        <EmptyState
          title="한정반 드롭을 불러오지 못했습니다."
          description="잠시 후 다시 시도해주세요."
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      )}

      {!isPending && !isError && data && data.content.length === 0 && (
        <EmptyState title="등록된 한정반 드롭이 없습니다." />
      )}

      {!isPending && !isError && data && data.content.length > 0 && (
        <div className={isPlaceholderData ? 'opacity-60' : ''}>
          <AdminLimitedDropTable
            drops={data.content}
            onSelect={(drop) => setSelectedId(drop.id)}
            onEdit={setEditing}
            onOpen={(drop) => setPendingAction({ type: 'open', drop })}
            onClose={(drop) => setPendingAction({ type: 'close', drop })}
          />

          <div className="mt-6">
            <Pagination page={page} totalPages={data.totalPages} onChange={updatePage} />
          </div>
        </div>
      )}

      <AdminLimitedDropFormModal
        open={creating || Boolean(editing)}
        onClose={() => {
          setCreating(false);
          setEditing(undefined);
        }}
        drop={editing}
      />

      <AdminLimitedDropDetailDrawer dropId={selectedId} onClose={() => setSelectedId(undefined)} />

      <ConfirmDialog
        open={Boolean(pendingAction)}
        onClose={() => setPendingAction(undefined)}
        onConfirm={handleConfirmAction}
        title={
          pendingAction?.type === 'open'
            ? '한정반 드롭을 오픈하시겠습니까?'
            : '한정반 드롭을 마감하시겠습니까?'
        }
        description={
          pendingAction?.type === 'open'
            ? 'Redis 재고 카운터가 즉시 초기화되고 구매가 시작됩니다.'
            : '즉시 판매가 종료되고 되돌릴 수 없습니다.'
        }
        confirmLabel={pendingAction?.type === 'open' ? '오픈' : '마감'}
        variant={pendingAction?.type === 'open' ? 'primary' : 'danger'}
        pending={pendingMutation.isPending}
      />
    </div>
  );
}
