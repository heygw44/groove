import type { OrderStatusGroup } from '@/types/order';
import { ORDER_STATUS_GROUPS, ORDER_STATUS_GROUP_LABEL } from '@/utils/orderStatus';

interface OrderStatusTabsProps {
  value?: OrderStatusGroup;
  onChange: (statusGroup?: OrderStatusGroup) => void;
}

const TAB_GROUPS: (OrderStatusGroup | undefined)[] = [undefined, ...ORDER_STATUS_GROUPS];

export function OrderStatusTabs({ value, onChange }: OrderStatusTabsProps) {
  return (
    <div role="group" aria-label="주문 상태" className="flex gap-1 overflow-x-auto pb-1">
      {TAB_GROUPS.map((statusGroup) => {
        const isSelected = statusGroup === value;
        return (
          <button
            key={statusGroup ?? 'ALL'}
            type="button"
            aria-pressed={isSelected}
            onClick={() => onChange(statusGroup)}
            className={`h-9 shrink-0 rounded-full px-4 text-sm whitespace-nowrap ${
              isSelected
                ? 'bg-content text-surface'
                : 'text-content-muted hover:bg-surface-muted hover:text-content'
            }`}
          >
            {statusGroup ? ORDER_STATUS_GROUP_LABEL[statusGroup] : '전체'}
          </button>
        );
      })}
    </div>
  );
}
