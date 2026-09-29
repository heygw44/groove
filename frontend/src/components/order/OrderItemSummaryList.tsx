import { OrderItemStatusBadge } from '@/components/order/OrderItemStatusBadge';
import type { OrderItemClaimStatus, OrderItemStatus } from '@/types/order';
import { formatPrice } from '@/utils/formatPrice';

export interface OrderSummaryItem {
  key: string | number;
  title: string;
  artistName?: string;
  thumbnailUrl?: string;
  price: number;
  quantity: number;
  lineAmount: number;
  /** 주문서 초안 등 결제 전 화면은 아직 없는 값이라 옵셔널로 둔다. */
  productOrderNumber?: string;
  status?: OrderItemStatus;
  claimStatus?: OrderItemClaimStatus;
}

interface OrderItemSummaryListProps {
  items: OrderSummaryItem[];
}

function OrderItemThumbnail({ url }: { url?: string }) {
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

export function OrderItemSummaryList({ items }: OrderItemSummaryListProps) {
  return (
    <div className="flex flex-col gap-4">
      {items.map((item) => (
        <div key={item.key} className="flex items-center gap-4">
          <OrderItemThumbnail url={item.thumbnailUrl} />

          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-1.5">
              <p className="truncate text-sm font-medium text-content">{item.title}</p>
              {item.status && (
                <OrderItemStatusBadge status={item.status} claimStatus={item.claimStatus} />
              )}
            </div>
            {item.productOrderNumber && (
              <p className="mt-0.5 font-mono text-xs text-content-muted">
                {item.productOrderNumber}
              </p>
            )}
            {item.artistName && (
              <p className="mt-0.5 text-xs text-content-muted">{item.artistName}</p>
            )}
            <p className="mt-1 text-xs text-content-muted">
              {formatPrice(item.price)} · 수량 {item.quantity}
            </p>
          </div>

          <p className="w-24 shrink-0 text-right text-sm font-bold">
            {formatPrice(item.lineAmount)}
          </p>
        </div>
      ))}
    </div>
  );
}
