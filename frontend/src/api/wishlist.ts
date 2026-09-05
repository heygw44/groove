import { client, unwrap } from '@/api/client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type { WishlistItem, WishlistListParams } from '@/types/wishlist';

export const getWishlist = (params: WishlistListParams) =>
  unwrap(client.get<ApiResponse<PageResponse<WishlistItem>>>('/wishlist', { params }));

export const addWishlist = (productId: number) =>
  unwrap(client.post<ApiResponse<WishlistItem>>('/wishlist', { productId }));

export const removeWishlist = async (productId: number) => {
  await client.delete<ApiResponse<void>>(`/wishlist/${productId}`);
};
