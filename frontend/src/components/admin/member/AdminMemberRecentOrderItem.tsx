import { Link } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import type { AdminMemberRecentOrder } from '@/types/adminMember';
import { formatServerDateTime } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';
import { ORDER_STATUS_BADGE, ORDER_STATUS_LABEL } from '@/utils/orderStatus';

interface AdminMemberRecentOrderItemProps {
  order: AdminMemberRecentOrder;
}

export function AdminMemberRecentOrderItem({ order }: AdminMemberRecentOrderItemProps) {
  const extraCount = order.itemCount - 1;

  return (
    <li className="flex items-center justify-between gap-3 rounded-md border border-line px-3 py-2 text-sm">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <Link
            to={`/admin/orders?keyword=${order.orderNumber}`}
            className="font-mono text-xs text-content hover:text-accent-hover"
          >
            {order.orderNumber}
          </Link>
          <Badge variant={ORDER_STATUS_BADGE[order.status]}>
            {ORDER_STATUS_LABEL[order.status]}
          </Badge>
        </div>
        <p className="truncate text-content-muted">
          {order.representativeProductName}
          {extraCount > 0 && ` 외 ${extraCount}개`}
        </p>
      </div>
      <div className="shrink-0 text-right">
        <p className="font-medium">
          {formatPrice(order.finalAmount - order.canceledAmount)}
          {order.canceledAmount > 0 && (
            <span className="ml-1 text-xs font-normal text-content-muted">
              (환불 {formatPrice(order.canceledAmount)})
            </span>
          )}
        </p>
        <p className="text-xs text-content-muted">{formatServerDateTime(order.createdAt)}</p>
      </div>
    </li>
  );
}
