import type { OrderStatus } from '@/types/order';
import { ORDER_STATUS_LABEL } from '@/utils/orderStatus';

interface OrderStatusTabsProps {
  value?: OrderStatus;
  onChange: (status?: OrderStatus) => void;
}

const TAB_STATUSES: (OrderStatus | undefined)[] = [
  undefined,
  'PENDING',
  'PAID',
  'PREPARING',
  'SHIPPED',
  'DELIVERED',
  'CANCELED',
];

export function OrderStatusTabs({ value, onChange }: OrderStatusTabsProps) {
  return (
    <div role="tablist" aria-label="주문 상태" className="flex gap-1 overflow-x-auto pb-1">
      {TAB_STATUSES.map((status) => {
        const isSelected = status === value;
        return (
          <button
            key={status ?? 'ALL'}
            type="button"
            role="tab"
            aria-selected={isSelected}
            onClick={() => onChange(status)}
            className={`h-9 shrink-0 rounded-full px-4 text-sm whitespace-nowrap ${
              isSelected
                ? 'bg-content text-surface'
                : 'text-content-muted hover:bg-surface-muted hover:text-content'
            }`}
          >
            {status ? ORDER_STATUS_LABEL[status] : '전체'}
          </button>
        );
      })}
    </div>
  );
}
