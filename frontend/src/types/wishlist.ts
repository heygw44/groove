import type { ProductStatus } from '@/types/product';

export interface WishlistItem {
  id: number;
  productId: number;
  title: string;
  artistName: string;
  thumbnailUrl?: string;
  price: number;
  productStatus: ProductStatus;
  stockQuantity: number;
  createdAt: string;
}

export interface WishlistListParams {
  page?: number;
  size?: number;
}

export interface WishlistAddRequest {
  productId: number;
}
