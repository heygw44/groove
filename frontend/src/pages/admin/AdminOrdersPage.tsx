import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { AdminOrderDetailDrawer } from '@/components/admin/AdminOrderDetailDrawer';
import { AdminOrderFilterBar } from '@/components/admin/AdminOrderFilterBar';
import { AdminOrderTable } from '@/components/admin/AdminOrderTable';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { Spinner } from '@/components/common/Spinner';
import { useAdminOrders } from '@/hooks/queries/useAdminOrders';
import type { AdminOrderSummary } from '@/types/order';
import {
  parseAdminOrderFilters,
  serializeAdminOrderFilters,
  toAdminOrderListParams,
  type AdminOrderFilters,
} from '@/utils/adminOrderFilters';

export default function AdminOrdersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = parseAdminOrderFilters(searchParams);

  const [selectedOrderId, setSelectedOrderId] = useState<number>();

  const { data, isPending, isError, isPlaceholderData, refetch } = useAdminOrders(
    toAdminOrderListParams(filters),
  );

  const updateFilters = (patch: Partial<AdminOrderFilters>, options?: { replace?: boolean }) => {
    setSearchParams(serializeAdminOrderFilters({ ...filters, ...patch, page: 0 }), options);
  };

  const updatePage = (page: number) => {
    setSearchParams(serializeAdminOrderFilters({ ...filters, page }));
  };

  const handleSelect = (order: AdminOrderSummary) => setSelectedOrderId(order.id);

  return (
    <div>
      <div className="mb-4 flex items-end justify-between gap-6">
        <div>
          <h2 className="text-[17px] font-bold tracking-tight">주문 관리</h2>
          <p className="mt-1.5 text-sm text-content-muted">
            {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}건`}
          </p>
        </div>
      </div>

      <div className="mb-4">
        <AdminOrderFilterBar filters={filters} onChange={updateFilters} />
      </div>

      {isPending && (
        <div className="flex min-h-48 items-center justify-center">
          <Spinner />
        </div>
      )}

      {!isPending && isError && (
        <EmptyState
          title="주문을 불러오지 못했습니다."
          description="잠시 후 다시 시도해주세요."
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      )}

      {!isPending && !isError && data && data.content.length === 0 && (
        <EmptyState title="조건에 맞는 주문이 없습니다." />
      )}

      {!isPending && !isError && data && data.content.length > 0 && (
        <div className={isPlaceholderData ? 'opacity-60' : ''}>
          <AdminOrderTable orders={data.content} onSelect={handleSelect} />

          <div className="mt-6">
            <Pagination page={filters.page} totalPages={data.totalPages} onChange={updatePage} />
          </div>
        </div>
      )}

      <AdminOrderDetailDrawer
        orderId={selectedOrderId}
        onClose={() => setSelectedOrderId(undefined)}
      />
    </div>
  );
}
