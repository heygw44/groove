import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  Cart,
  CartItem,
  CartItemAddRequest,
  CartItemQuantityUpdateRequest,
} from '@/types/cart';

export const getCart = () => unwrap(client.get<ApiResponse<Cart>>('/cart'));

export const addCartItem = (payload: CartItemAddRequest) =>
  unwrap(client.post<ApiResponse<CartItem>>('/cart/items', payload));

export const updateCartItemQuantity = (
  cartItemId: number,
  payload: CartItemQuantityUpdateRequest,
) => unwrap(client.patch<ApiResponse<CartItem>>(`/cart/items/${cartItemId}`, payload));

export const removeCartItem = async (cartItemId: number) => {
  await client.delete<ApiResponse<void>>(`/cart/items/${cartItemId}`);
};
