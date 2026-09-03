import type { BadgeVariant } from '@/components/common/Badge';
import type { OrderStatus } from '@/types/order';

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
  PENDING: '결제대기',
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
