import type {
  AdminOrderClaimCounts,
  AdminOrderClaimStatusCounts,
  AdminOrderClaimSummary,
  AdminOrderItemSummary,
  OrderClaimStatus,
  OrderClaimType,
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

/** 가상계좌 결제는 구매자 환불계좌가 있어야 환불되므로 서버가 관리자 판매취소를 거절한다. */
export const isSaleCancelBlockedByRefundAccount = (item: AdminOrderItemSummary): boolean =>
  item.virtualAccountPayment;

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

const CLAIM_STATUS_LABEL: Record<OrderClaimType, Record<OrderClaimStatus, string>> = {
  CANCEL: {
    REQUESTED: '취소요청',
    COLLECTING: '수거중',
    DONE: '취소완료',
    REJECTED: '취소거부',
    WITHDRAWN: '요청철회',
  },
  RETURN: {
    REQUESTED: '반품요청',
    COLLECTING: '수거중',
    DONE: '반품완료',
    REJECTED: '반품거부',
    WITHDRAWN: '요청철회',
  },
};

export const getClaimStatusLabel = (type: OrderClaimType, status: OrderClaimStatus): string =>
  CLAIM_STATUS_LABEL[type][status];

/** 취소 클레임은 수거 단계를 거치지 않는다. */
export const CLAIM_STATUSES_BY_TYPE: Record<OrderClaimType, readonly OrderClaimStatus[]> = {
  CANCEL: ['REQUESTED', 'DONE', 'REJECTED', 'WITHDRAWN'],
  RETURN: ['REQUESTED', 'COLLECTING', 'DONE', 'REJECTED', 'WITHDRAWN'],
};

export const CLAIM_TYPE_DESCRIPTION: Record<OrderClaimType, string> = {
  CANCEL:
    '배송 준비 중 상품의 취소요청을 승인하면 바로 환불됩니다. 결제완료 상품은 구매자가 즉시 취소해 취소완료로 바로 쌓입니다.',
  RETURN:
    '배송완료 후 7일 안에 들어온 반품요청입니다. 수거를 시작하고, 상품이 도착하면 반품완료로 처리해 환불합니다.',
};

const CLAIM_COUNT_FIELD: Record<OrderClaimStatus, keyof AdminOrderClaimStatusCounts> = {
  REQUESTED: 'requested',
  COLLECTING: 'collecting',
  DONE: 'done',
  REJECTED: 'rejected',
  WITHDRAWN: 'withdrawn',
};

export const pickClaimCounts = (
  counts: AdminOrderClaimCounts,
  type: OrderClaimType,
): AdminOrderClaimStatusCounts => (type === 'CANCEL' ? counts.cancel : counts.returns);

export const pickClaimCount = (
  counts: AdminOrderClaimStatusCounts,
  status: OrderClaimStatus,
): number => counts[CLAIM_COUNT_FIELD[status]];

/** 관리자가 처리해야 하는 건수. 반품은 수거를 시작한 뒤에도 반품완료 처리가 남아 수거중까지 센다. */
export const countActionableClaims = (
  counts: AdminOrderClaimStatusCounts,
  type: OrderClaimType,
): number => (type === 'RETURN' ? counts.requested + counts.collecting : counts.requested);
