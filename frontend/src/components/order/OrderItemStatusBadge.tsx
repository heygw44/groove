import { Badge } from '@/components/common/Badge';
import type { OrderItemClaimStatus, OrderItemStatus } from '@/types/order';
import { getOrderItemDisplayStatus } from '@/utils/orderItemStatus';

interface OrderItemStatusBadgeProps {
  status: OrderItemStatus;
  claimStatus?: OrderItemClaimStatus;
}

/** 클레임이 진행 중이면 클레임 라벨을, 아니면 상품주문 상태 라벨을 보여준다. */
export function OrderItemStatusBadge({ status, claimStatus }: OrderItemStatusBadgeProps) {
  const { label, variant } = getOrderItemDisplayStatus(status, claimStatus);
  return <Badge variant={variant}>{label}</Badge>;
}
