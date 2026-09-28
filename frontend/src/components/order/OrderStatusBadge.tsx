import { Badge } from '@/components/common/Badge';
import type { OrderStatus } from '@/types/order';
import type { PaymentStatus } from '@/types/payment';
import { getOrderDisplayStatus } from '@/utils/orderStatus';

interface OrderStatusBadgeProps {
  status: OrderStatus;
  /** PENDING + WAITING_FOR_DEPOSIT 이면 "입금대기"로 파생 표시한다. */
  paymentStatus?: PaymentStatus;
}

export function OrderStatusBadge({ status, paymentStatus }: OrderStatusBadgeProps) {
  const { label, variant } = getOrderDisplayStatus(status, paymentStatus);
  return <Badge variant={variant}>{label}</Badge>;
}
