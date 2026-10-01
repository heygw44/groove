import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  approveAdminOrderClaim,
  cancelAdminOrderItem,
  collectAdminOrderClaim,
  completeAdminOrderClaim,
  confirmAdminOrderItems,
  deliverAdminOrderItems,
  rejectAdminOrderClaim,
  shipAdminOrderItems,
} from '@/api/admin';
import {
  adminOrderClaimKeys,
  adminOrderItemKeys,
  adminOrderKeys,
  adminProductKeys,
  adminStatsKeys,
} from '@/hooks/queries/queryKeys';
import type {
  AdminOrderClaimCompleteRequest,
  AdminOrderClaimRejectRequest,
  AdminOrderItemCancelRequest,
  AdminOrderItemConfirmRequest,
  AdminOrderItemDeliverRequest,
  AdminOrderItemShipRequest,
} from '@/types/adminOrder';

/**
 * 상품주문·주문 상세·클레임·대시보드 카운트가 모두 같은 상태에서 파생되므로 함께 무효화한다.
 * 실패해도 서버 상태가 바뀌었을 수 있어(예: 환불 실패 시 클레임 거부) 끝나면 항상 무효화한다.
 * 재고를 되돌리는 처리(판매취소·취소 승인·반품 완료)는 관리자 상품 목록의 재고도 바뀌므로 함께 무효화한다.
 */
const useInvalidateAdminOrders = ({ restoresStock = false }: { restoresStock?: boolean } = {}) => {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: adminOrderItemKeys.all }),
      queryClient.invalidateQueries({ queryKey: adminOrderKeys.all }),
      queryClient.invalidateQueries({ queryKey: adminOrderClaimKeys.all }),
      queryClient.invalidateQueries({ queryKey: adminStatsKeys.all }),
      ...(restoresStock ? [queryClient.invalidateQueries({ queryKey: adminProductKeys.all })] : []),
    ]);
};

export const useConfirmAdminOrderItems = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: (payload: AdminOrderItemConfirmRequest) => confirmAdminOrderItems(payload),
    onSettled: () => invalidate(),
  });
};

export const useShipAdminOrderItems = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: (payload: AdminOrderItemShipRequest) => shipAdminOrderItems(payload),
    onSettled: () => invalidate(),
  });
};

export const useDeliverAdminOrderItems = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: (payload: AdminOrderItemDeliverRequest) => deliverAdminOrderItems(payload),
    onSettled: () => invalidate(),
  });
};

export const useCancelAdminOrderItem = () => {
  const invalidate = useInvalidateAdminOrders({ restoresStock: true });

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: AdminOrderItemCancelRequest }) =>
      cancelAdminOrderItem(id, payload),
    onSettled: () => invalidate(),
  });
};

export const useApproveAdminOrderClaim = () => {
  const invalidate = useInvalidateAdminOrders({ restoresStock: true });

  return useMutation({
    mutationFn: (claimId: number) => approveAdminOrderClaim(claimId),
    onSettled: () => invalidate(),
  });
};

export const useRejectAdminOrderClaim = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: ({
      claimId,
      payload,
    }: {
      claimId: number;
      payload: AdminOrderClaimRejectRequest;
    }) => rejectAdminOrderClaim(claimId, payload),
    onSettled: () => invalidate(),
  });
};

export const useCollectAdminOrderClaim = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: (claimId: number) => collectAdminOrderClaim(claimId),
    onSettled: () => invalidate(),
  });
};

export const useCompleteAdminOrderClaim = () => {
  const invalidate = useInvalidateAdminOrders({ restoresStock: true });

  return useMutation({
    mutationFn: ({
      claimId,
      payload,
    }: {
      claimId: number;
      payload: AdminOrderClaimCompleteRequest;
    }) => completeAdminOrderClaim(claimId, payload),
    onSettled: () => invalidate(),
  });
};
