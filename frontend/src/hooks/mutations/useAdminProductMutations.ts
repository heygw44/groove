import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  adjustStock,
  createAdminProduct,
  hideAdminProduct,
  restoreAdminProduct,
  updateAdminProduct,
} from '@/api/admin';
import { adminProductKeys, productKeys } from '@/hooks/queries/queryKeys';
import type {
  AdminProductCreateRequest,
  AdminProductUpdateRequest,
  StockAdjustRequest,
} from '@/types/product';

/*
 * 목록(products/adminProducts)은 항상 무효화한다. 상세(['product', id])는
 * ['products'] prefix 에 안 걸리므로 id 가 있을 때만 별도로 무효화한다.
 */
const useInvalidateProducts = () => {
  const queryClient = useQueryClient();
  return (id?: number) => {
    queryClient.invalidateQueries({ queryKey: productKeys.all });
    queryClient.invalidateQueries({ queryKey: adminProductKeys.all });
    if (id !== undefined) {
      queryClient.invalidateQueries({ queryKey: productKeys.detail(id) });
    }
  };
};

export const useCreateProduct = () => {
  const invalidate = useInvalidateProducts();

  return useMutation({
    mutationFn: (payload: AdminProductCreateRequest) => createAdminProduct(payload),
    onSuccess: () => invalidate(),
  });
};

export const useUpdateProduct = () => {
  const invalidate = useInvalidateProducts();

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: AdminProductUpdateRequest }) =>
      updateAdminProduct(id, payload),
    onSuccess: (_data, { id }) => invalidate(id),
  });
};

export const useHideProduct = () => {
  const invalidate = useInvalidateProducts();

  return useMutation({
    mutationFn: (id: number) => hideAdminProduct(id),
    onSuccess: (_data, id) => invalidate(id),
  });
};

export const useRestoreProduct = () => {
  const invalidate = useInvalidateProducts();

  return useMutation({
    mutationFn: (id: number) => restoreAdminProduct(id),
    onSuccess: (_data, id) => invalidate(id),
  });
};

export const useAdjustStock = () => {
  const invalidate = useInvalidateProducts();

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: StockAdjustRequest }) =>
      adjustStock(id, payload),
    onSuccess: (_data, { id }) => invalidate(id),
  });
};
