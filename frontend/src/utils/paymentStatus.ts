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
  CANCEL_REQUESTED: '취소 확인 중',
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

/** 토스 결과가 DB 에 아직 확정되지 않아 대사 스케줄러가 처리해야 하는 상태인지. */
export const isReconcilePending = (status: PaymentStatus): boolean =>
  RECONCILE_PENDING_STATUSES.has(status);
