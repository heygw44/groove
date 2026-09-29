import { Link } from 'react-router-dom';

import { OrderItemStatusBadge } from '@/components/order/OrderItemStatusBadge';
import type { OrderListItem } from '@/types/order';
import { formatPrice } from '@/utils/formatPrice';

interface OrderItemRowProps {
  item: OrderListItem;
}

function OrderItemThumbnail({ url }: { url: string | null }) {
  return (
    <div className="h-14 w-14 shrink-0 overflow-hidden rounded-md bg-surface-muted">
      {url ? (
        <img src={url} alt="" loading="lazy" className="h-full w-full object-cover" />
      ) : (
        <div className="flex h-full w-full items-center justify-center text-content-subtle">
          <svg
            width="20"
            height="20"
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

export function OrderItemRow({ item }: OrderItemRowProps) {
  return (
    <div className="flex items-center gap-3">
      <OrderItemThumbnail url={item.thumbnailUrl} />

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-1.5">
          <Link
            to={`/products/${item.productId}`}
            className="line-clamp-1 text-sm font-medium text-content hover:underline"
          >
            {item.productName}
          </Link>
          <OrderItemStatusBadge status={item.status} claimStatus={item.claimStatus} />
        </div>
        <p className="mt-0.5 text-xs text-content-muted">수량 {item.quantity}개</p>
      </div>

      <p className="shrink-0 text-sm font-medium text-content">{formatPrice(item.lineAmount)}</p>
    </div>
  );
}
