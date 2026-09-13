import type { BadgeVariant } from '@/components/common/Badge';
import type { PaymentStatus } from '@/types/payment';

export const PAYMENT_STATUSES: readonly PaymentStatus[] = [
  'READY',
  'DONE',
  'CANCELED',
  'FAILED',
  'UNKNOWN',
  'CANCEL_REQUESTED',
];

export const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  READY: '승인대기',
  DONE: '결제완료',
  CANCELED: '결제취소',
  FAILED: '결제실패',
  UNKNOWN: '결과 확인 중',
  CANCEL_REQUESTED: '취소 처리 중',
};

export const PAYMENT_STATUS_BADGE: Record<PaymentStatus, BadgeVariant> = {
  READY: 'accent',
  DONE: 'success',
  CANCELED: 'danger',
  FAILED: 'danger',
  UNKNOWN: 'accent',
  CANCEL_REQUESTED: 'accent',
};

const RECONCILE_PENDING_STATUSES = new Set<PaymentStatus>(['UNKNOWN', 'CANCEL_REQUESTED']);

export const CANCEL_REQUESTED_MESSAGES = {
  memberReason: '취소 결과를 확인하고 있어 다시 취소할 수 없습니다.',
  adminReason: '취소 결과를 확인하고 있어 주문 상태를 변경할 수 없습니다.',
  success: '취소 요청이 접수됐습니다. 환불 확인까지 잠시 걸릴 수 있습니다.',
} as const;

const ORDER_CANCEL_SUCCESS_MESSAGE = '주문을 취소했습니다.';

/** 토스 결과가 DB 에 아직 확정되지 않아 대사 스케줄러가 처리해야 하는 상태인지. */
export const isReconcilePending = (status: PaymentStatus): boolean =>
  RECONCILE_PENDING_STATUSES.has(status);

export const isCancellationPending = (status?: PaymentStatus): boolean =>
  status === 'CANCEL_REQUESTED';

export const getOrderCancelSuccessMessage = (status?: PaymentStatus): string =>
  isCancellationPending(status) ? CANCEL_REQUESTED_MESSAGES.success : ORDER_CANCEL_SUCCESS_MESSAGE;
