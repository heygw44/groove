import type { BadgeVariant } from '@/components/common/Badge';
import type { AdminOrderItemResult } from '@/types/adminOrder';
import type { OrderDetail, OrderItem } from '@/types/order';
import type { OrderPayment, PaymentStatus } from '@/types/payment';

export const PAYMENT_STATUSES: readonly PaymentStatus[] = [
  'READY',
  'DONE',
  'PARTIAL_CANCELED',
  'CANCELED',
  'FAILED',
  'UNKNOWN',
  'CANCEL_REQUESTED',
  'WAITING_FOR_DEPOSIT',
];

export const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  READY: '승인대기',
  DONE: '결제완료',
  PARTIAL_CANCELED: '부분취소',
  CANCELED: '결제취소',
  FAILED: '결제실패',
  UNKNOWN: '결과 확인 중',
  CANCEL_REQUESTED: '취소 처리 중',
  WAITING_FOR_DEPOSIT: '입금대기',
};

export const PAYMENT_STATUS_BADGE: Record<PaymentStatus, BadgeVariant> = {
  READY: 'accent',
  DONE: 'success',
  PARTIAL_CANCELED: 'accent',
  CANCELED: 'danger',
  FAILED: 'danger',
  UNKNOWN: 'accent',
  CANCEL_REQUESTED: 'accent',
  WAITING_FOR_DEPOSIT: 'accent',
};

const RECONCILE_PENDING_STATUSES = new Set<PaymentStatus>(['UNKNOWN', 'CANCEL_REQUESTED']);

export const CANCEL_REQUESTED_MESSAGES = {
  memberReason: '취소 결과를 확인하고 있어 다시 취소할 수 없습니다.',
  success: '취소 요청이 접수됐습니다. 환불 확인까지 잠시 걸릴 수 있습니다.',
} as const;

const ORDER_CANCEL_SUCCESS_MESSAGE = '주문을 취소했습니다.';
const PARTIAL_REFUND_UNCONFIRMED_MESSAGE =
  '일부 상품의 환불 결과를 확인하고 있습니다. 확인되면 나머지 상품을 다시 취소해 주세요.';

/** 토스 결과가 DB 에 아직 확정되지 않아 대사 스케줄러가 처리해야 하는 상태인지. */
export const isReconcilePending = (status: PaymentStatus): boolean =>
  RECONCILE_PENDING_STATUSES.has(status);

export const isCancellationPending = (status?: PaymentStatus): boolean =>
  status === 'CANCEL_REQUESTED';

export const hasRefundInProgress = (items: OrderItem[]): boolean =>
  items.some((item) => item.refundInProgress);

const PARTIAL_CANCELED_MESSAGE = '일부 상품만 취소됐습니다. 남은 상품을 확인해 주세요.';
const ITEM_CANCEL_REQUESTED_MESSAGE = '취소 요청이 접수됐습니다. 승인되면 취소됩니다.';
const ADMIN_REFUND_CONFIRMING_MESSAGE = '처리를 접수했습니다. 환불 결과를 확인하고 있습니다.';

const hasCancelAction = (items: OrderItem[]): boolean =>
  items.some(
    (item) =>
      item.availableActions.includes('CANCEL') || item.availableActions.includes('CANCEL_REQUEST'),
  );

/** 상품 취소 응답으로 실제 결과를 가린다. 환불 결과 불명·승인 대기로 바뀐 경우를 취소 완료로 안내하지 않는다. */
export const getOrderItemCancelSuccessMessage = (item: OrderItem): string => {
  if (item.refundInProgress) {
    return CANCEL_REQUESTED_MESSAGES.success;
  }
  return item.status === 'CANCELED' ? ORDER_CANCEL_SUCCESS_MESSAGE : ITEM_CANCEL_REQUESTED_MESSAGE;
};

export const getAdminClaimApproveMessage = (
  result: Pick<AdminOrderItemResult, 'status'>,
): string =>
  result.status === 'CANCELED' ? '취소 클레임을 승인했습니다.' : ADMIN_REFUND_CONFIRMING_MESSAGE;

export const getAdminClaimCompleteMessage = (
  result: Pick<AdminOrderItemResult, 'status'>,
): string =>
  result.status === 'RETURNED' ? '반품 수거를 완료했습니다.' : ADMIN_REFUND_CONFIRMING_MESSAGE;

export const getAdminSaleCancelMessage = (
  result: Pick<AdminOrderItemResult, 'status'>,
  productOrderNumber: string,
): string =>
  result.status === 'CANCELED'
    ? `${productOrderNumber} 판매취소 처리했습니다.`
    : ADMIN_REFUND_CONFIRMING_MESSAGE;

export const getOrderCancelSuccessMessage = (order: OrderDetail): string => {
  // 일부 상품 환불이 미확정이면 서버가 그 상품을 취소하지 못하고 넘어간다.
  if (hasRefundInProgress(order.items)) {
    return PARTIAL_REFUND_UNCONFIRMED_MESSAGE;
  }
  if (hasCancelAction(order.items)) {
    return PARTIAL_CANCELED_MESSAGE;
  }
  return isCancellationPending(order.payment?.status)
    ? CANCEL_REQUESTED_MESSAGES.success
    : ORDER_CANCEL_SUCCESS_MESSAGE;
};

/** 입금이 끝난 가상계좌 결제는 환불 때 구매자 환불계좌가 필요하다. */
export const requiresRefundAccount = (payment?: OrderPayment): boolean =>
  Boolean(payment?.virtualAccount) &&
  (payment?.status === 'DONE' || payment?.status === 'PARTIAL_CANCELED');

const NO_PAYMENT_LABEL = '결제 전';
const VIRTUAL_ACCOUNT_LABEL = '무통장입금';

/**
 * 결제정보 카드의 결제수단 줄. 간편결제(easyPayProvider) > 무통장입금(virtualAccount) > 원문
 * method 순으로 고른다. 결제 이력이 아직 없으면 "결제 전"을 보여준다.
 */
export const getPaymentMethodLabel = (payment?: OrderPayment): string => {
  if (!payment) {
    return NO_PAYMENT_LABEL;
  }
  if (payment.easyPayProvider) {
    return payment.easyPayProvider;
  }
  if (payment.virtualAccount) {
    return payment.status === 'WAITING_FOR_DEPOSIT'
      ? `${VIRTUAL_ACCOUNT_LABEL} (${PAYMENT_STATUS_LABEL.WAITING_FOR_DEPOSIT})`
      : VIRTUAL_ACCOUNT_LABEL;
  }
  return payment.method;
};
