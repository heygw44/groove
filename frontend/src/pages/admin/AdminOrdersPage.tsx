import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { AdminOrderDetailDrawer } from '@/components/admin/AdminOrderDetailDrawer';
import { AdminOrderItemBulkResultNotice } from '@/components/admin/AdminOrderItemBulkResultNotice';
import { AdminOrderItemFilterBar } from '@/components/admin/AdminOrderItemFilterBar';
import { AdminOrderItemTable } from '@/components/admin/AdminOrderItemTable';
import { AdminReasonModal } from '@/components/admin/AdminReasonModal';
import { AdminShipModal } from '@/components/admin/AdminShipModal';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { TableSkeleton } from '@/components/common/TableSkeleton';
import { useToast } from '@/components/common/toastContext';
import {
  useCancelAdminOrderItem,
  useConfirmAdminOrderItems,
  useDeliverAdminOrderItems,
} from '@/hooks/mutations/useAdminOrderMutations';
import { useAdminOrderItemCount } from '@/hooks/queries/useAdminOrderItemCount';
import { useAdminOrderItems } from '@/hooks/queries/useAdminOrderItems';
import { useFallbackPageRedirect } from '@/hooks/useFallbackPageRedirect';
import type { AdminOrderItemBulkResult, AdminOrderItemSummary } from '@/types/adminOrder';
import { canConfirmItem, canDeliverItem, canShipItem } from '@/utils/adminOrderActions';
import {
  ADMIN_ORDER_PAGE_SIZE,
  parseAdminOrderFilters,
  serializeAdminOrderFilters,
  toAdminOrderItemCountParams,
  toAdminOrderItemListParams,
  type AdminOrderFilters,
} from '@/utils/adminOrderFilters';
import { getErrorMessage } from '@/utils/apiError';
import { getFallbackPage, toTotalPages } from '@/utils/pagination';

interface BulkResult {
  label: string;
  result: AdminOrderItemBulkResult;
}

export default function AdminOrdersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = parseAdminOrderFilters(searchParams);
  const { showToast } = useToast();

  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [isShipOpen, setIsShipOpen] = useState(false);
  const [cancelTarget, setCancelTarget] = useState<AdminOrderItemSummary>();
  const [bulkResult, setBulkResult] = useState<BulkResult>();
  const [detailOrderId, setDetailOrderId] = useState<number>();

  const { data, isPending, isError, error, isPlaceholderData, refetch } = useAdminOrderItems(
    toAdminOrderItemListParams(filters),
  );

  const count = useAdminOrderItemCount(toAdminOrderItemCountParams(filters));

  const confirmMutation = useConfirmAdminOrderItems();
  const deliverMutation = useDeliverAdminOrderItems();
  const cancelMutation = useCancelAdminOrderItem();

  const items = data?.content ?? [];
  const totalPages = count.data && toTotalPages(count.data.totalElements, ADMIN_ORDER_PAGE_SIZE);
  const isListSettled = data !== undefined && !isPlaceholderData;
  // 처리 뒤 무효화 때는 옛 건수가 placeholder 아닌 채로 남는다. 새 건수가 와야 마지막 페이지를 믿는다.
  const isCountSettled = count.data !== undefined && !count.isPlaceholderData && !count.isFetching;
  const fallbackPage =
    isListSettled && isCountSettled && totalPages !== undefined
      ? getFallbackPage(filters.page, data.content.length, totalPages)
      : undefined;
  const isMovingToFallbackPage = fallbackPage !== undefined;

  useFallbackPageRedirect(fallbackPage);

  const selectedItems = items.filter((item) => selectedIds.includes(item.id));
  const confirmable = selectedItems.filter(canConfirmItem);
  const shippable = selectedItems.filter(canShipItem);
  const deliverable = selectedItems.filter(canDeliverItem);

  const updateFilters = (patch: Partial<AdminOrderFilters>, options?: { replace?: boolean }) => {
    setSelectedIds([]);
    setSearchParams(serializeAdminOrderFilters({ ...filters, ...patch, page: 0 }), options);
  };

  const updatePage = (page: number) => {
    setSelectedIds([]);
    setSearchParams(serializeAdminOrderFilters({ ...filters, page }));
  };

  const toggleItem = (id: number) =>
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((selectedId) => selectedId !== id) : [...prev, id],
    );

  const toggleAll = (checked: boolean) => {
    if (isPlaceholderData) {
      return;
    }
    setSelectedIds(checked ? items.map((item) => item.id) : []);
  };

  const openCancel = (target: AdminOrderItemSummary) => {
    cancelMutation.reset();
    setCancelTarget(target);
  };

  const finishBulk = (label: string, result: AdminOrderItemBulkResult) => {
    setBulkResult({ label, result });
    setSelectedIds([]);
    setIsShipOpen(false);
  };

  const handleConfirm = () =>
    confirmMutation.mutate(
      { orderItemIds: confirmable.map((item) => item.id) },
      {
        onSuccess: (result) => finishBulk('발주확인', result),
        onError: (failure) => showToast('error', getErrorMessage(failure)),
      },
    );

  const handleDeliver = () =>
    deliverMutation.mutate(
      { orderItemIds: deliverable.map((item) => item.id) },
      {
        onSuccess: (result) => finishBulk('배송완료', result),
        onError: (failure) => showToast('error', getErrorMessage(failure)),
      },
    );

  const handleCancel = (target: AdminOrderItemSummary, reason: string) =>
    cancelMutation.mutate(
      { id: target.id, payload: { reason: reason === '' ? undefined : reason } },
      {
        onSuccess: () => {
          showToast('success', `${target.productOrderNumber} 판매취소 처리했습니다.`);
          setCancelTarget(undefined);
        },
      },
    );

  const countLabel = (() => {
    if (count.isPending) {
      return '불러오는 중…';
    }
    return count.data ? `상품주문 총 ${count.data.totalElements}건` : '상품주문 총 -건';
  })();

  return (
    <div>
      <div className="mb-4">
        <h2 className="text-[17px] font-bold tracking-tight">주문 관리</h2>
        <p className="mt-1.5 text-sm text-content-muted">{countLabel}</p>
      </div>

      <div className="mb-4">
        <AdminOrderItemFilterBar filters={filters} onChange={updateFilters} />
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <span className="text-sm text-content-muted">선택 {selectedItems.length}건</span>
        <Button
          variant="secondary"
          size="sm"
          disabled={confirmable.length === 0}
          loading={confirmMutation.isPending}
          onClick={handleConfirm}
        >
          발주확인 ({confirmable.length})
        </Button>
        <Button
          variant="secondary"
          size="sm"
          disabled={shippable.length === 0}
          onClick={() => setIsShipOpen(true)}
        >
          발송처리 ({shippable.length})
        </Button>
        <Button
          variant="secondary"
          size="sm"
          disabled={deliverable.length === 0}
          loading={deliverMutation.isPending}
          onClick={handleDeliver}
        >
          배송완료 ({deliverable.length})
        </Button>
      </div>

      {bulkResult && (
        <div className="mb-4">
          <AdminOrderItemBulkResultNotice
            label={bulkResult.label}
            result={bulkResult.result}
            onDismiss={() => setBulkResult(undefined)}
          />
        </div>
      )}

      {(isPending || isMovingToFallbackPage) && <TableSkeleton columns={9} />}

      {!isPending && !isMovingToFallbackPage && isError && (
        <QueryErrorState error={error} onRetry={refetch} title="상품주문을 불러오지 못했습니다" />
      )}

      {!isPending && !isMovingToFallbackPage && !isError && data && items.length === 0 && (
        <EmptyState title="조건에 맞는 상품주문이 없습니다" />
      )}

      {!isPending && !isError && data && items.length > 0 && (
        <div className={isPlaceholderData ? 'opacity-60' : ''}>
          <AdminOrderItemTable
            items={items}
            selectedIds={selectedIds}
            onToggle={toggleItem}
            onToggleAll={toggleAll}
            onCancel={openCancel}
            onOpenOrder={setDetailOrderId}
            selectionDisabled={isPlaceholderData}
          />

          {totalPages !== undefined && (
            <div className="mt-6">
              <Pagination page={filters.page} totalPages={totalPages} onChange={updatePage} />
            </div>
          )}
        </div>
      )}

      <AdminOrderDetailDrawer orderId={detailOrderId} onClose={() => setDetailOrderId(undefined)} />

      {isShipOpen && (
        <AdminShipModal
          items={shippable}
          onClose={() => setIsShipOpen(false)}
          onCompleted={(result) => finishBulk('발송처리', result)}
        />
      )}

      {cancelTarget && (
        <AdminReasonModal
          title="판매취소"
          description={`${cancelTarget.productOrderNumber} 를 판매취소하고 즉시 환불합니다.`}
          label="취소 사유(선택)"
          submitLabel="판매취소"
          required={false}
          pending={cancelMutation.isPending}
          errorMessage={cancelMutation.isError ? getErrorMessage(cancelMutation.error) : undefined}
          onClose={() => setCancelTarget(undefined)}
          onSubmit={(reason) => handleCancel(cancelTarget, reason)}
        />
      )}
    </div>
  );
}
