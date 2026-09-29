import type {
  AdminOrderClaimSummary,
  AdminOrderItemSummary,
  OrderClaimStatus,
} from '@/types/adminOrder';
import type { OrderItemClaimStatus } from '@/types/order';

const IN_PROGRESS_CLAIM_STATUSES: readonly OrderItemClaimStatus[] = [
  'CANCEL_REQUEST',
  'RETURN_REQUEST',
  'COLLECTING',
];

/** 서버 OrderItemClaimStatus.isInProgress 와 같은 기준. 진행 중 클레임이 있으면 이행 처리가 건너뛰어진다. */
const hasClaimInProgress = (item: AdminOrderItemSummary): boolean =>
  item.claimStatus !== undefined && IN_PROGRESS_CLAIM_STATUSES.includes(item.claimStatus);

export const canConfirmItem = (item: AdminOrderItemSummary): boolean =>
  item.status === 'PAID' && !hasClaimInProgress(item);

export const canShipItem = (item: AdminOrderItemSummary): boolean =>
  item.status === 'PREPARING' && !hasClaimInProgress(item);

export const canDeliverItem = (item: AdminOrderItemSummary): boolean =>
  item.status === 'SHIPPING' && !hasClaimInProgress(item);

/** 판매취소는 결제완료·배송준비 단계에서만 관리자가 직접 시작할 수 있다. */
export const canCancelItem = (item: AdminOrderItemSummary): boolean =>
  (item.status === 'PAID' || item.status === 'PREPARING') && !hasClaimInProgress(item);

export type AdminClaimAction = 'approve' | 'collect' | 'complete' | 'reject';

/** 클레임 종류·상태별로 서버가 허용하는 처리만 돌려준다. */
export const getClaimActions = (
  claim: Pick<AdminOrderClaimSummary, 'type' | 'status'>,
): AdminClaimAction[] => {
  if (claim.status === 'REQUESTED') {
    return claim.type === 'CANCEL' ? ['approve', 'reject'] : ['collect', 'reject'];
  }
  if (claim.status === 'COLLECTING') {
    return ['complete', 'reject'];
  }
  return [];
};

export const CLAIM_STATUS_LABEL: Record<OrderClaimStatus, string> = {
  REQUESTED: '접수',
  COLLECTING: '수거중',
  DONE: '완료',
  REJECTED: '거부',
  WITHDRAWN: '철회',
};
