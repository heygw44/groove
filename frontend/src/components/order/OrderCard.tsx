import { Link } from 'react-router-dom';

import { Skeleton } from '@/components/common/Skeleton';
import { OrderItemRow } from '@/components/order/OrderItemRow';
import type { OrderSummary } from '@/types/order';
import { formatServerDate } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';

interface OrderCardProps {
  order: OrderSummary;
}

export function OrderCard({ order }: OrderCardProps) {
  const hasItems = order.items.length > 0;
  const fallbackLabel =
    order.itemCount > 1
      ? `${order.representativeProductName} 외 ${order.itemCount - 1}건`
      : order.representativeProductName;

  return (
    <li className="list-none rounded-lg border border-line bg-surface px-5 py-4">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line pb-3">
        <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
          <span className="text-sm text-content-muted">{formatServerDate(order.createdAt)}</span>
          <span className="truncate font-mono text-xs text-content-muted">{order.orderNumber}</span>
        </div>
        <Link
          to={`/orders/${order.id}`}
          className="shrink-0 text-sm text-content-muted hover:text-content"
        >
          주문 상세 &gt;
        </Link>
      </div>

      <div className="flex flex-col gap-3 py-4">
        {hasItems ? (
          order.items.map((item, index) => (
            <OrderItemRow key={`${item.productId}-${index}`} item={item} />
          ))
        ) : (
          <p className="truncate text-sm text-content">{fallbackLabel}</p>
        )}
      </div>

      <div className="flex items-center justify-end gap-2 border-t border-line pt-3">
        {order.discountAmount > 0 && (
          <span className="text-xs text-content-muted">
            쿠폰 -{formatPrice(order.discountAmount)}
          </span>
        )}
        <span className="text-sm text-content-muted">결제금액</span>
        <span className="text-sm font-bold">{formatPrice(order.finalAmount)}</span>
      </div>
    </li>
  );
}

export function OrderCardSkeleton() {
  return (
    <li className="rounded-lg border border-line bg-surface px-5 py-4">
      <div className="flex items-center justify-between gap-2 border-b border-line pb-3">
        <div className="flex items-center gap-2">
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-3 w-28" />
        </div>
        <Skeleton className="h-4 w-16" />
      </div>
      <div className="flex items-center gap-3 py-4">
        <Skeleton className="h-14 w-14 shrink-0" />
        <div className="min-w-0 flex-1">
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="mt-1.5 h-3 w-1/4" />
        </div>
        <Skeleton className="h-4 w-14" />
      </div>
      <div className="flex items-center justify-end gap-2 border-t border-line pt-3">
        <Skeleton className="h-4 w-20" />
      </div>
    </li>
  );
}
