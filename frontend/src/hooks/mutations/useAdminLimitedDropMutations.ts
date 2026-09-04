import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  closeAdminLimitedDrop,
  createAdminLimitedDrop,
  openAdminLimitedDrop,
  updateAdminLimitedDrop,
} from '@/api/admin';
import { adminLimitedDropKeys, limitedDropKeys } from '@/hooks/queries/queryKeys';
import type { AdminLimitedDropCreateRequest, AdminLimitedDropUpdateRequest } from '@/types/limitedDrop';

const useInvalidateLimitedDrops = () => {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: adminLimitedDropKeys.all });
    // 공개 목록/상세의 재고·상태도 함께 바뀌므로 같이 무효화한다.
    queryClient.invalidateQueries({ queryKey: limitedDropKeys.all });
  };
};

export const useCreateAdminLimitedDrop = () => {
  const invalidate = useInvalidateLimitedDrops();

  return useMutation({
    mutationFn: (payload: AdminLimitedDropCreateRequest) => createAdminLimitedDrop(payload),
    onSuccess: () => invalidate(),
  });
};

export const useUpdateAdminLimitedDrop = () => {
  const invalidate = useInvalidateLimitedDrops();

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: AdminLimitedDropUpdateRequest }) =>
      updateAdminLimitedDrop(id, payload),
    onSuccess: () => invalidate(),
  });
};

export const useOpenAdminLimitedDrop = () => {
  const invalidate = useInvalidateLimitedDrops();

  return useMutation({
    mutationFn: (id: number) => openAdminLimitedDrop(id),
    onSuccess: () => invalidate(),
  });
};

export const useCloseAdminLimitedDrop = () => {
  const invalidate = useInvalidateLimitedDrops();

  return useMutation({
    mutationFn: (id: number) => closeAdminLimitedDrop(id),
    onSuccess: () => invalidate(),
  });
};
