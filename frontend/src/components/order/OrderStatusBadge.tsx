import { Badge } from '@/components/common/Badge';
import type { OrderStatus } from '@/types/order';
import { ORDER_STATUS_BADGE, ORDER_STATUS_LABEL } from '@/utils/orderStatus';

interface OrderStatusBadgeProps {
  status: OrderStatus;
}

export function OrderStatusBadge({ status }: OrderStatusBadgeProps) {
  return <Badge variant={ORDER_STATUS_BADGE[status]}>{ORDER_STATUS_LABEL[status]}</Badge>;
}
