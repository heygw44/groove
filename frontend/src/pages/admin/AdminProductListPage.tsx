import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { AdminProductFilterBar } from '@/components/admin/AdminProductFilterBar';
import { AdminProductTable } from '@/components/admin/AdminProductTable';
import { StockAdjustModal } from '@/components/admin/StockAdjustModal';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { LinkButton } from '@/components/common/LinkButton';
import { Pagination } from '@/components/common/Pagination';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { TableSkeleton } from '@/components/common/TableSkeleton';
import { useToast } from '@/components/common/toastContext';
import { useHideProduct, useRestoreProduct } from '@/hooks/mutations/useAdminProductMutations';
import { useAdminProducts } from '@/hooks/queries/useAdminProducts';
import type { AdminProductSummary, ProductStatus } from '@/types/product';
import { getErrorMessage } from '@/utils/apiError';

const PRODUCT_STATUSES = new Set<string>(['ON_SALE', 'SOLD_OUT', 'HIDDEN']);

const PAGE_SIZE = 20;
const KEYWORD_MAX_LENGTH = 100;

const parseStatus = (value: string | null): ProductStatus | undefined =>
  value && PRODUCT_STATUSES.has(value) ? (value as ProductStatus) : undefined;

const parseKeyword = (value: string | null): string =>
  (value ?? '').trim().slice(0, KEYWORD_MAX_LENGTH);

const parsePage = (value: string | null): number => {
  if (value === null || !/^\d+$/.test(value)) {
    return 0;
  }
  return Number(value);
};

export default function AdminProductListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseStatus(searchParams.get('status'));
  const keyword = parseKeyword(searchParams.get('keyword'));
  const page = parsePage(searchParams.get('page'));

  const [adjusting, setAdjusting] = useState<AdminProductSummary | undefined>(undefined);
  const [hiding, setHiding] = useState<AdminProductSummary | undefined>(undefined);
  const [restoring, setRestoring] = useState<AdminProductSummary | undefined>(undefined);

  const { showToast } = useToast();
  const { data, isPending, isError, error, isPlaceholderData, refetch } = useAdminProducts({
    status,
    keyword: keyword === '' ? undefined : keyword,
    page,
    size: PAGE_SIZE,
  });
  const hideMutation = useHideProduct();
  const restoreMutation = useRestoreProduct();

  const updateFilters = (
    next: { keyword?: string; status?: ProductStatus },
    options?: { replace?: boolean },
  ) => {
    setSearchParams(
      (prev) => {
        const params = new URLSearchParams(prev);
        if ('keyword' in next) {
          if (next.keyword) {
            params.set('keyword', next.keyword);
          } else {
            params.delete('keyword');
          }
        }
        if ('status' in next) {
          if (next.status) {
            params.set('status', next.status);
          } else {
            params.delete('status');
          }
        }
        params.delete('page');
        return params;
      },
      { replace: options?.replace },
    );
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
          <h2 className="text-[17px] font-bold tracking-tight">상품 관리</h2>
          <p className="mt-1.5 text-sm text-content-muted">
            {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}개`}
          </p>
        </div>
        <LinkButton to="/admin/products/new">상품 등록</LinkButton>
      </div>

      <div className="mb-4">
        <AdminProductFilterBar filters={{ keyword, status }} onChange={updateFilters} />
      </div>

      {isPending && <TableSkeleton columns={7} />}

      {!isPending && isError && (
        <QueryErrorState error={error} onRetry={refetch} title="상품을 불러오지 못했습니다" />
      )}

      {!isPending && !isError && data && data.content.length === 0 && (
        <EmptyState
          title={keyword === '' ? '조건에 맞는 상품이 없습니다' : '검색 결과가 없습니다'}
          description={keyword === '' ? undefined : '다른 검색어를 입력해주세요.'}
        />
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
        title="상품을 숨기시겠습니까?"
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
        title="상품을 복구하시겠습니까?"
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
