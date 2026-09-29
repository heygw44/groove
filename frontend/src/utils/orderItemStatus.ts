import type { BadgeVariant } from '@/components/common/Badge';
import type { OrderItemClaimStatus, OrderItemStatus } from '@/types/order';

/** PAYMENT_PENDING 은 결제 전 내부 상태라 화면에 노출하지 않는다. */
export const ORDER_ITEM_STATUSES: readonly OrderItemStatus[] = [
  'PAYMENT_WAITING',
  'PAID',
  'PREPARING',
  'SHIPPING',
  'DELIVERED',
  'PURCHASE_CONFIRMED',
  'CANCELED',
  'RETURNED',
  'CANCELED_BY_NOPAYMENT',
];

export const ORDER_ITEM_STATUS_LABEL: Record<OrderItemStatus, string> = {
  PAYMENT_PENDING: '결제대기',
  PAYMENT_WAITING: '입금대기',
  PAID: '결제완료',
  PREPARING: '배송준비',
  SHIPPING: '배송중',
  DELIVERED: '배송완료',
  PURCHASE_CONFIRMED: '구매확정',
  CANCELED: '취소완료',
  RETURNED: '반품완료',
  CANCELED_BY_NOPAYMENT: '미입금취소',
};

export const ORDER_ITEM_STATUS_BADGE: Record<OrderItemStatus, BadgeVariant> = {
  PAYMENT_PENDING: 'neutral',
  PAYMENT_WAITING: 'accent',
  PAID: 'success',
  PREPARING: 'success',
  SHIPPING: 'success',
  DELIVERED: 'neutral',
  PURCHASE_CONFIRMED: 'neutral',
  CANCELED: 'danger',
  RETURNED: 'danger',
  CANCELED_BY_NOPAYMENT: 'danger',
};

export const ORDER_ITEM_CLAIM_STATUSES: readonly OrderItemClaimStatus[] = [
  'CANCEL_REQUEST',
  'CANCEL_DONE',
  'CANCEL_REJECT',
  'RETURN_REQUEST',
  'COLLECTING',
  'RETURN_DONE',
  'RETURN_REJECT',
];

export const ORDER_ITEM_CLAIM_STATUS_LABEL: Record<OrderItemClaimStatus, string> = {
  CANCEL_REQUEST: '취소요청',
  CANCEL_DONE: '취소완료',
  CANCEL_REJECT: '취소거부',
  RETURN_REQUEST: '반품요청',
  COLLECTING: '수거중',
  RETURN_DONE: '반품완료',
  RETURN_REJECT: '반품거부',
};

export const ORDER_ITEM_CLAIM_STATUS_BADGE: Record<OrderItemClaimStatus, BadgeVariant> = {
  CANCEL_REQUEST: 'accent',
  CANCEL_DONE: 'danger',
  CANCEL_REJECT: 'neutral',
  RETURN_REQUEST: 'accent',
  COLLECTING: 'accent',
  RETURN_DONE: 'danger',
  RETURN_REJECT: 'neutral',
};

/** 이 세 클레임 상태만 "진행 중"이라 배지에서 상품주문 상태보다 우선한다. */
const IN_PROGRESS_CLAIM_STATUSES = new Set<OrderItemClaimStatus>([
  'CANCEL_REQUEST',
  'RETURN_REQUEST',
  'COLLECTING',
]);

export interface OrderItemDisplayStatus {
  label: string;
  variant: BadgeVariant;
}

/**
 * 상품주문 배지에 쓰는 파생 상태. 클레임이 진행 중이면 클레임 라벨을 보여주고,
 * 클레임이 없거나 이미 끝났으면(승인 완료로 상태가 이미 CANCELED/RETURNED 로
 * 넘어갔거나, 거부로 원래 상태로 돌아간 경우) 상품주문 상태 라벨을 보여준다.
 */
export const getOrderItemDisplayStatus = (
  status: OrderItemStatus,
  claimStatus?: OrderItemClaimStatus,
): OrderItemDisplayStatus => {
  if (claimStatus !== undefined && IN_PROGRESS_CLAIM_STATUSES.has(claimStatus)) {
    return {
      label: ORDER_ITEM_CLAIM_STATUS_LABEL[claimStatus],
      variant: ORDER_ITEM_CLAIM_STATUS_BADGE[claimStatus],
    };
  }
  return { label: ORDER_ITEM_STATUS_LABEL[status], variant: ORDER_ITEM_STATUS_BADGE[status] };
};
