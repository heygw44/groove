import { Link } from 'react-router-dom';

import { OrderStatusBadge } from '@/components/order/OrderStatusBadge';
import type { OrderSummary } from '@/types/order';
import { formatDateTime } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';

interface OrderCardProps {
  order: OrderSummary;
}

function OrderThumbnail({ url }: { url?: string }) {
  return (
    <div className="h-16 w-16 shrink-0 overflow-hidden rounded-md bg-surface-muted">
      {url ? (
        <img src={url} alt="" loading="lazy" className="h-full w-full object-cover" />
      ) : (
        <div className="flex h-full w-full items-center justify-center text-content-subtle">
          <svg
            width="24"
            height="24"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.4"
            aria-hidden
          >
            <circle cx="12" cy="12" r="9" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        </div>
      )}
    </div>
  );
}

export function OrderCard({ order }: OrderCardProps) {
  const productLabel =
    order.itemCount > 1
      ? `${order.representativeProductName} 외 ${order.itemCount - 1}건`
      : order.representativeProductName;

  return (
    <li className="list-none">
      <Link
        to={`/orders/${order.id}`}
        className="flex items-center gap-4 rounded-lg border border-line bg-surface px-5 py-4 no-underline hover:border-line-strong hover:no-underline"
      >
        <OrderThumbnail url={order.thumbnailUrl} />

        <div className="min-w-0 flex-1">
          <p className="font-mono text-xs text-content-muted">{order.orderNumber}</p>
          <p className="mt-0.5 truncate text-sm font-medium text-content">{productLabel}</p>
          <p className="mt-1 text-xs text-content-muted">{formatDateTime(order.createdAt)}</p>
        </div>

        <div className="flex shrink-0 flex-col items-end gap-2">
          <OrderStatusBadge status={order.status} />
          <p className="text-sm font-bold">{formatPrice(order.finalAmount)}</p>
        </div>
      </Link>
    </li>
  );
}
