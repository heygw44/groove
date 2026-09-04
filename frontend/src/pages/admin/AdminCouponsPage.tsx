import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { AdminCouponFormModal } from '@/components/admin/AdminCouponFormModal';
import { AdminCouponTable } from '@/components/admin/AdminCouponTable';
import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { Select } from '@/components/common/Select';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { useDisableAdminCoupon } from '@/hooks/mutations/useAdminCouponMutations';
import { useAdminCoupons } from '@/hooks/queries/useAdminCoupons';
import type { AdminCouponSummary, CouponStatus } from '@/types/coupon';
import { getErrorMessage } from '@/utils/apiError';

const STATUS_OPTIONS: { value: CouponStatus | ''; label: string }[] = [
  { value: '', label: '전체' },
  { value: 'ACTIVE', label: '활성' },
  { value: 'DISABLED', label: '비활성' },
];

const STATUS_VALUES = new Set<string>(
  STATUS_OPTIONS.map((option) => option.value).filter((value): value is CouponStatus =>
    Boolean(value),
  ),
);

const PAGE_SIZE = 20;

const parseStatus = (value: string | null): CouponStatus | undefined =>
  value && STATUS_VALUES.has(value) ? (value as CouponStatus) : undefined;

const parsePage = (value: string | null): number => {
  if (value === null || !/^\d+$/.test(value)) {
    return 0;
  }
  return Number(value);
};

export default function AdminCouponsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseStatus(searchParams.get('status'));
  const page = parsePage(searchParams.get('page'));

  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<AdminCouponSummary | undefined>(undefined);
  const [disabling, setDisabling] = useState<AdminCouponSummary | undefined>(undefined);

  const { showToast } = useToast();
  const { data, isPending, isError, isPlaceholderData, refetch } = useAdminCoupons({
    status,
    page,
    size: PAGE_SIZE,
  });
  const disableMutation = useDisableAdminCoupon();

  const updateStatus = (next: CouponStatus | '') => {
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

  const handleDisable = () => {
    if (!disabling) {
      return;
    }
    disableMutation.mutate(disabling.id, {
      onSuccess: () => {
        showToast('success', '쿠폰을 비활성화했습니다.');
        setDisabling(undefined);
      },
      onError: (error) => {
        setDisabling(undefined);
        showToast('error', getErrorMessage(error));
      },
    });
  };

  return (
    <div>
      <div className="mb-4 flex items-end justify-between gap-6">
        <div>
          <h2 className="text-[17px] font-bold tracking-tight">쿠폰 관리</h2>
          <p className="mt-1.5 text-sm text-content-muted">
            {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}개`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Select
            aria-label="상태 필터"
            value={status ?? ''}
            onChange={(event) => updateStatus(event.target.value as CouponStatus | '')}
            className="w-32"
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
          <Button onClick={() => setCreating(true)}>쿠폰 등록</Button>
        </div>
      </div>

      {isPending && (
        <div className="flex min-h-48 items-center justify-center">
          <Spinner />
        </div>
      )}

      {!isPending && isError && (
        <EmptyState
          title="쿠폰을 불러오지 못했습니다."
          description="잠시 후 다시 시도해주세요."
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      )}

      {!isPending && !isError && data && data.content.length === 0 && (
        <EmptyState title="등록된 쿠폰이 없습니다." />
      )}

      {!isPending && !isError && data && data.content.length > 0 && (
        <div className={isPlaceholderData ? 'opacity-60' : ''}>
          <AdminCouponTable coupons={data.content} onEdit={setEditing} onDisable={setDisabling} />

          <div className="mt-6">
            <Pagination page={page} totalPages={data.totalPages} onChange={updatePage} />
          </div>
        </div>
      )}

      <AdminCouponFormModal
        open={creating || Boolean(editing)}
        onClose={() => {
          setCreating(false);
          setEditing(undefined);
        }}
        coupon={editing}
      />

      <ConfirmDialog
        open={Boolean(disabling)}
        onClose={() => setDisabling(undefined)}
        onConfirm={handleDisable}
        title="쿠폰을 비활성화하시겠습니까?"
        description="즉시 만료되어 회원이 더 이상 발급받거나 사용할 수 없습니다. 되돌릴 수 없습니다."
        confirmLabel="비활성화"
        variant="danger"
        pending={disableMutation.isPending}
      />
    </div>
  );
}
