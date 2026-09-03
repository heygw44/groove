import { useMutation, useQueryClient } from '@tanstack/react-query';

import { addCartItem, removeCartItem, updateCartItemQuantity } from '@/api/cart';
import { cartKeys } from '@/hooks/queries/queryKeys';
import type { Cart, CartItemAddRequest } from '@/types/cart';

export const useAddCartItem = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CartItemAddRequest) => addCartItem(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: cartKeys.all }),
  });
};

export const useUpdateCartItemQuantity = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ cartItemId, quantity }: { cartItemId: number; quantity: number }) =>
      updateCartItemQuantity(cartItemId, { quantity }),
    /*
     * 스테퍼 연타 시 즉시 반영되도록 낙관적으로 갱신하고, 실패하면 서버 값으로
     * 복원한다.
     */
    onMutate: async ({ cartItemId, quantity }) => {
      await queryClient.cancelQueries({ queryKey: cartKeys.all });
      const previous = queryClient.getQueryData<Cart>(cartKeys.all);

      if (previous) {
        const items = previous.items.map((item) =>
          item.id === cartItemId ? { ...item, quantity, subtotal: item.price * quantity } : item,
        );
        queryClient.setQueryData<Cart>(cartKeys.all, {
          ...previous,
          items,
          totalAmount: items.reduce((sum, item) => sum + item.subtotal, 0),
        });
      }

      return { previous };
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(cartKeys.all, context.previous);
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: cartKeys.all }),
  });
};

export const useRemoveCartItem = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (cartItemId: number) => removeCartItem(cartItemId),
    // 삭제도 같은 이유로 낙관적으로 반영하고, 실패하면 서버 값으로 복원한다.
    onMutate: async (cartItemId) => {
      await queryClient.cancelQueries({ queryKey: cartKeys.all });
      const previous = queryClient.getQueryData<Cart>(cartKeys.all);

      if (previous) {
        const items = previous.items.filter((item) => item.id !== cartItemId);
        queryClient.setQueryData<Cart>(cartKeys.all, {
          ...previous,
          items,
          totalAmount: items.reduce((sum, item) => sum + item.subtotal, 0),
        });
      }

      return { previous };
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(cartKeys.all, context.previous);
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: cartKeys.all }),
  });
};
