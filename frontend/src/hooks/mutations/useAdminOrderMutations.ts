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

/** 상품주문·주문 상세·클레임·대시보드 카운트가 모두 같은 상태에서 파생되므로 함께 무효화한다. */
const useInvalidateAdminOrders = () => {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: adminOrderItemKeys.all }),
      queryClient.invalidateQueries({ queryKey: adminOrderKeys.all }),
      queryClient.invalidateQueries({ queryKey: adminOrderClaimKeys.all }),
      queryClient.invalidateQueries({ queryKey: adminStatsKeys.all }),
    ]);
};

export const useConfirmAdminOrderItems = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: (payload: AdminOrderItemConfirmRequest) => confirmAdminOrderItems(payload),
    onSuccess: () => invalidate(),
  });
};

export const useShipAdminOrderItems = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: (payload: AdminOrderItemShipRequest) => shipAdminOrderItems(payload),
    onSuccess: () => invalidate(),
  });
};

export const useDeliverAdminOrderItems = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: (payload: AdminOrderItemDeliverRequest) => deliverAdminOrderItems(payload),
    onSuccess: () => invalidate(),
  });
};

export const useCancelAdminOrderItem = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: AdminOrderItemCancelRequest }) =>
      cancelAdminOrderItem(id, payload),
    onSuccess: () => invalidate(),
  });
};

export const useApproveAdminOrderClaim = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: (claimId: number) => approveAdminOrderClaim(claimId),
    onSuccess: () => invalidate(),
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
    onSuccess: () => invalidate(),
  });
};

export const useCollectAdminOrderClaim = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: (claimId: number) => collectAdminOrderClaim(claimId),
    onSuccess: () => invalidate(),
  });
};

export const useCompleteAdminOrderClaim = () => {
  const invalidate = useInvalidateAdminOrders();

  return useMutation({
    mutationFn: ({
      claimId,
      payload,
    }: {
      claimId: number;
      payload: AdminOrderClaimCompleteRequest;
    }) => completeAdminOrderClaim(claimId, payload),
    onSuccess: () => invalidate(),
  });
};
