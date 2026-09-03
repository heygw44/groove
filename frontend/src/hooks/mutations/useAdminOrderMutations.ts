import { useMutation, useQueryClient } from '@tanstack/react-query';

import { changeAdminOrderStatus } from '@/api/admin';
import { adminOrderKeys } from '@/hooks/queries/queryKeys';
import type { AdminOrderDetail, OrderStatus } from '@/types/order';

export const useChangeAdminOrderStatus = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ orderId, status }: { orderId: number; status: OrderStatus }) =>
      changeAdminOrderStatus(orderId, { status }),
    onSuccess: (data: AdminOrderDetail, { orderId }) => {
      queryClient.setQueryData(adminOrderKeys.detail(orderId), data);
      queryClient.invalidateQueries({ queryKey: adminOrderKeys.lists });
    },
  });
};
