import { useSearchParams } from 'react-router-dom';

import { EmptyState } from '@/components/common/EmptyState';
import { LinkButton } from '@/components/common/LinkButton';
import { Pagination } from '@/components/common/Pagination';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { OrderCard, OrderCardSkeleton } from '@/components/order/OrderCard';
import { OrderStatusTabs } from '@/components/order/OrderStatusTabs';
import { useOrders } from '@/hooks/queries/useOrders';
import type { OrderStatus } from '@/types/order';
import {
  parseOrderListFilters,
  serializeOrderListFilters,
  toOrderListParams,
} from '@/utils/orderFilters';

const SKELETON_COUNT = 5;

export default function OrderListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = parseOrderListFilters(searchParams);

  const { data, isPending, isError, error, isPlaceholderData, refetch } = useOrders(
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
          <QueryErrorState
            error={error}
            onRetry={refetch}
            title="주문 내역을 불러오지 못했습니다."
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
