import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { AdminProductTable } from '@/components/admin/AdminProductTable';
import { StockAdjustModal } from '@/components/admin/StockAdjustModal';
import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { Select } from '@/components/common/Select';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { useHideProduct, useRestoreProduct } from '@/hooks/mutations/useAdminProductMutations';
import { useAdminProducts } from '@/hooks/queries/useAdminProducts';
import type { AdminProductSummary, ProductStatus } from '@/types/product';
import { getErrorMessage } from '@/utils/apiError';

const STATUS_OPTIONS: { value: ProductStatus | ''; label: string }[] = [
  { value: '', label: '전체' },
  { value: 'ON_SALE', label: '판매중' },
  { value: 'SOLD_OUT', label: '품절' },
  { value: 'HIDDEN', label: '숨김' },
];

const STATUS_VALUES = new Set<string>(
  STATUS_OPTIONS.map((option) => option.value).filter((value): value is ProductStatus =>
    Boolean(value),
  ),
);

const PAGE_SIZE = 20;

const parseStatus = (value: string | null): ProductStatus | undefined =>
  value && STATUS_VALUES.has(value) ? (value as ProductStatus) : undefined;

const parsePage = (value: string | null): number => {
  if (value === null || !/^\d+$/.test(value)) {
    return 0;
  }
  return Number(value);
};

export default function AdminProductListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseStatus(searchParams.get('status'));
  const page = parsePage(searchParams.get('page'));

  const [adjusting, setAdjusting] = useState<AdminProductSummary | undefined>(undefined);
  const [hiding, setHiding] = useState<AdminProductSummary | undefined>(undefined);
  const [restoring, setRestoring] = useState<AdminProductSummary | undefined>(undefined);

  const { showToast } = useToast();
  const { data, isPending, isError, isPlaceholderData, refetch } = useAdminProducts({
    status,
    page,
    size: PAGE_SIZE,
  });
  const hideMutation = useHideProduct();
  const restoreMutation = useRestoreProduct();

  const updateStatus = (next: ProductStatus | '') => {
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

  const handleHide = () => {
    if (!hiding) {
      return;
    }
    hideMutation.mutate(hiding.id, {
      onSuccess: () => {
        showToast('success', '상품을 숨겼습니다.');
        setHiding(undefined);
      },
      onError: (error) => {
        setHiding(undefined);
        showToast('error', getErrorMessage(error));
      },
    });
  };

  const handleRestore = () => {
    if (!restoring) {
      return;
    }
    restoreMutation.mutate(restoring.id, {
      onSuccess: () => {
        showToast('success', '상품을 복구했습니다.');
        setRestoring(undefined);
      },
      onError: (error) => {
        setRestoring(undefined);
        showToast('error', getErrorMessage(error));
      },
    });
  };

  return (
    <div>
      <div className="mb-4 flex items-end justify-between gap-6">
        <div>
          <h2 className="text-[17px] font-bold tracking-tight">상품</h2>
          <p className="mt-1.5 text-sm text-content-muted">
            {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}개`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Select
            aria-label="상태 필터"
            value={status ?? ''}
            onChange={(event) => updateStatus(event.target.value as ProductStatus | '')}
            className="w-32"
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
          <Link to="/admin/products/new">
            <Button>상품 등록</Button>
          </Link>
        </div>
      </div>

      {isPending && (
        <div className="flex min-h-48 items-center justify-center">
          <Spinner />
        </div>
      )}

      {!isPending && isError && (
        <EmptyState
          title="상품을 불러오지 못했습니다."
          description="잠시 후 다시 시도해주세요."
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      )}

      {!isPending && !isError && data && data.content.length === 0 && (
        <EmptyState title="조건에 맞는 상품이 없습니다." />
      )}

      {!isPending && !isError && data && data.content.length > 0 && (
        <div className={isPlaceholderData ? 'opacity-60' : ''}>
          <AdminProductTable
            products={data.content}
            onAdjustStock={setAdjusting}
            onHide={setHiding}
            onRestore={setRestoring}
            disabled={hideMutation.isPending || restoreMutation.isPending}
          />

          <div className="mt-6">
            <Pagination page={page} totalPages={data.totalPages} onChange={updatePage} />
          </div>
        </div>
      )}

      <StockAdjustModal
        open={Boolean(adjusting)}
        onClose={() => setAdjusting(undefined)}
        product={adjusting}
      />

      <ConfirmDialog
        open={Boolean(hiding)}
        onClose={() => setHiding(undefined)}
        onConfirm={handleHide}
        title="상품을 숨길까요?"
        description={
          hiding
            ? `'${hiding.title}' 상품이 판매 목록에서 사라집니다. 숨긴 상품은 목록에서 복구할 수 있습니다.`
            : ''
        }
        confirmLabel="숨김"
        pending={hideMutation.isPending}
      />

      <ConfirmDialog
        open={Boolean(restoring)}
        onClose={() => setRestoring(undefined)}
        onConfirm={handleRestore}
        title="상품을 복구할까요?"
        description={
          restoring
            ? `'${restoring.title}' 상품이 다시 노출됩니다. 재고가 있으면 판매중, 없으면 품절 상태로 돌아갑니다.`
            : ''
        }
        confirmLabel="복구"
        variant="primary"
        pending={restoreMutation.isPending}
      />
    </div>
  );
}
