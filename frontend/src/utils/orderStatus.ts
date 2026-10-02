import type { BadgeVariant } from '@/components/common/Badge';
import type { OrderStatus, OrderStatusGroup } from '@/types/order';
import type { PaymentStatus } from '@/types/payment';
import { PAYMENT_STATUS_BADGE, PAYMENT_STATUS_LABEL } from '@/utils/paymentStatus';

export const ORDER_STATUSES: readonly OrderStatus[] = ['PENDING', 'PAID', 'CANCELED'];

const ORDER_STATUS_SET = new Set<string>(ORDER_STATUSES);

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  // 결제 전 주문은 목록에 나타나지 않으므로(placed_at 없음), 보이는 PENDING 은 가상계좌 입금대기뿐이다.
  PENDING: '입금대기',
  PAID: '결제완료',
  CANCELED: '취소',
};

export const ORDER_STATUS_BADGE: Record<OrderStatus, BadgeVariant> = {
  PENDING: 'accent',
  PAID: 'success',
  CANCELED: 'danger',
};

// 구매자 입력 사유가 'constructor' 같은 프로토타입 키와 겹쳐도 안전하도록 Map 으로 둔다.
const SYSTEM_CANCEL_REASON_LABEL = new Map<string, string>([
  ['EXPIRED', '입금기한 만료'],
  ['SUPERSEDED', '다른 결제로 변경'],
]);

/** 시스템이 남긴 취소 사유 코드는 문구로 바꾸고, 구매자가 입력한 사유는 그대로 돌려준다. */
export const formatCancelReason = (reason: string): string =>
  SYSTEM_CANCEL_REASON_LABEL.get(reason) ?? reason;

export const isOrderStatus = (value: unknown): value is OrderStatus =>
  typeof value === 'string' && ORDER_STATUS_SET.has(value);

/** 주문 목록 탭(`?statusGroup=`) 값. 생략하면 전체. */
export const ORDER_STATUS_GROUPS: readonly OrderStatusGroup[] = [
  'PAYMENT_WAITING',
  'PAID',
  'PREPARING',
  'SHIPPING',
  'DELIVERED',
  'PURCHASE_CONFIRMED',
  'CANCEL_RETURN',
];

const ORDER_STATUS_GROUP_SET = new Set<string>(ORDER_STATUS_GROUPS);

export const ORDER_STATUS_GROUP_LABEL: Record<OrderStatusGroup, string> = {
  PAYMENT_WAITING: '입금대기',
  PAID: '결제완료',
  PREPARING: '배송준비',
  SHIPPING: '배송중',
  DELIVERED: '배송완료',
  PURCHASE_CONFIRMED: '구매확정',
  CANCEL_RETURN: '취소·반품',
};

export const isOrderStatusGroup = (value: unknown): value is OrderStatusGroup =>
  typeof value === 'string' && ORDER_STATUS_GROUP_SET.has(value);

export interface OrderDisplayStatus {
  label: string;
  variant: BadgeVariant;
}

/**
 * 주문 배지에 쓰는 파생 상태. 결제가 WAITING_FOR_DEPOSIT 이면 결제정보의 라벨·배지를
 * 그대로 쓰고, 아니면 주문 상태 라벨을 쓴다(둘 다 현재는 "입금대기"로 같다).
 */
export const getOrderDisplayStatus = (
  status: OrderStatus,
  paymentStatus?: PaymentStatus,
): OrderDisplayStatus => {
  if (status === 'PENDING' && paymentStatus === 'WAITING_FOR_DEPOSIT') {
    return {
      label: PAYMENT_STATUS_LABEL.WAITING_FOR_DEPOSIT,
      variant: PAYMENT_STATUS_BADGE.WAITING_FOR_DEPOSIT,
    };
  }
  return { label: ORDER_STATUS_LABEL[status], variant: ORDER_STATUS_BADGE[status] };
};
