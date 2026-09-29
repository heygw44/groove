import type { BadgeVariant } from '@/components/common/Badge';
import type { OrderStatus, OrderStatusGroup } from '@/types/order';
import type { PaymentStatus } from '@/types/payment';
import { PAYMENT_STATUS_BADGE, PAYMENT_STATUS_LABEL } from '@/utils/paymentStatus';

export const ORDER_STATUSES: readonly OrderStatus[] = [
  'PENDING',
  'PAID',
  'PREPARING',
  'SHIPPED',
  'DELIVERED',
  'CANCELED',
  'REFUNDED',
];

const ORDER_STATUS_SET = new Set<string>(ORDER_STATUSES);

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  // 결제 전 주문은 목록에 나타나지 않으므로(placed_at 없음), 보이는 PENDING 은 가상계좌 입금대기뿐이다.
  PENDING: '입금대기',
  PAID: '결제완료',
  PREPARING: '배송준비',
  SHIPPED: '배송중',
  DELIVERED: '배송완료',
  CANCELED: '취소',
  REFUNDED: '환불',
};

export const ORDER_STATUS_BADGE: Record<OrderStatus, BadgeVariant> = {
  PENDING: 'accent',
  PAID: 'success',
  PREPARING: 'success',
  SHIPPED: 'success',
  DELIVERED: 'neutral',
  CANCELED: 'danger',
  REFUNDED: 'danger',
};

/** 주문 상세 타임라인에 쓰는 정상 흐름 5단계. */
export const ORDER_STATUS_STEPS = ['PENDING', 'PAID', 'PREPARING', 'SHIPPED', 'DELIVERED'] as const;

const CANCELABLE_STATUSES = new Set<OrderStatus>(['PENDING', 'PAID']);

export const isCancelableStatus = (status: OrderStatus): boolean => CANCELABLE_STATUSES.has(status);

/** 관리자 상태 전이표. 나머지 상태는 전이 불가(빈 배열). */
export const ADMIN_ORDER_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  PENDING: [],
  PAID: ['PREPARING', 'CANCELED'],
  PREPARING: ['SHIPPED', 'CANCELED'],
  SHIPPED: ['DELIVERED'],
  DELIVERED: [],
  CANCELED: [],
  REFUNDED: [],
};

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
