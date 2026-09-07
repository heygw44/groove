import { useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { LinkButton } from '@/components/common/LinkButton';
import { Pagination } from '@/components/common/Pagination';
import { Skeleton } from '@/components/common/Skeleton';
import { OrderCard } from '@/components/order/OrderCard';
import { OrderStatusTabs } from '@/components/order/OrderStatusTabs';
import { useOrders } from '@/hooks/queries/useOrders';
import type { OrderStatus } from '@/types/order';
import {
  parseOrderListFilters,
  serializeOrderListFilters,
  toOrderListParams,
} from '@/utils/orderFilters';

const SKELETON_COUNT = 5;

function OrderCardSkeleton() {
  return (
    <li className="flex items-center gap-4 rounded-lg border border-line bg-surface px-5 py-4">
      <Skeleton className="h-16 w-16 shrink-0" />
      <div className="min-w-0 flex-1">
        <Skeleton className="h-3 w-1/3" />
        <Skeleton className="mt-1.5 h-4 w-2/3" />
        <Skeleton className="mt-1.5 h-3 w-1/4" />
      </div>
      <div className="flex shrink-0 flex-col items-end gap-2">
        <Skeleton className="h-5 w-14" />
        <Skeleton className="h-4 w-16" />
      </div>
    </li>
  );
}

export default function OrderListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = parseOrderListFilters(searchParams);

  const { data, isPending, isError, isPlaceholderData, refetch } = useOrders(
    toOrderListParams(filters),
  );

  const updateStatus = (status?: OrderStatus) => {
    // 상태 탭을 바꾸면 이전 페이지 번호는 의미가 없으니 첫 페이지로 되돌린다.
    setSearchParams(serializeOrderListFilters({ status, page: 0 }));
  };

  const updatePage = (page: number) => {
    setSearchParams(serializeOrderListFilters({ ...filters, page }));
  };

  return (
    <div>
      <h2 className="text-xl font-bold">주문 내역</h2>

      <div className="mt-5">
        <OrderStatusTabs value={filters.status} onChange={updateStatus} />
      </div>

      <p className="mt-4 text-sm text-content-muted">
        {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}건`}
      </p>

      <div className="mt-3">
        {isPending && (
          <ul className="flex flex-col gap-3">
            {Array.from({ length: SKELETON_COUNT }, (_, index) => (
              <OrderCardSkeleton key={index} />
            ))}
          </ul>
        )}

        {!isPending && isError && (
          <EmptyState
            title="주문 내역을 불러오지 못했습니다"
            description="잠시 후 다시 시도해주세요."
            action={
              <Button variant="secondary" onClick={() => refetch()}>
                다시 시도
              </Button>
            }
          />
        )}

        {!isPending && !isError && data && data.content.length === 0 && (
          <EmptyState
            title="주문 내역이 없습니다"
            action={
              <LinkButton to="/products" variant="secondary">
                상품 보러 가기
              </LinkButton>
            }
          />
        )}

        {!isPending && !isError && data && data.content.length > 0 && (
          <div className={isPlaceholderData ? 'opacity-60' : ''}>
            <ul className="flex flex-col gap-3">
              {data.content.map((order) => (
                <OrderCard key={order.id} order={order} />
              ))}
            </ul>

            <div className="mt-6">
              <Pagination page={filters.page} totalPages={data.totalPages} onChange={updatePage} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
