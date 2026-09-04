import { useMutation, useQueryClient } from '@tanstack/react-query';

import { createAdminCoupon, disableAdminCoupon, updateAdminCoupon } from '@/api/admin';
import { adminCouponKeys } from '@/hooks/queries/queryKeys';
import type { AdminCouponCreateRequest, AdminCouponUpdateRequest } from '@/types/coupon';

const useInvalidateAdminCoupons = () => {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: adminCouponKeys.all });
};

export const useCreateAdminCoupon = () => {
  const invalidate = useInvalidateAdminCoupons();

  return useMutation({
    mutationFn: (payload: AdminCouponCreateRequest) => createAdminCoupon(payload),
    onSuccess: () => invalidate(),
  });
};

export const useUpdateAdminCoupon = () => {
  const invalidate = useInvalidateAdminCoupons();

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: AdminCouponUpdateRequest }) =>
      updateAdminCoupon(id, payload),
    onSuccess: () => invalidate(),
  });
};

export const useDisableAdminCoupon = () => {
  const invalidate = useInvalidateAdminCoupons();

  return useMutation({
    mutationFn: (id: number) => disableAdminCoupon(id),
    onSuccess: () => invalidate(),
  });
};
