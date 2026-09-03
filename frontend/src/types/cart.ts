import type { ProductStatus } from '@/types/product';

export interface CartItem {
  id: number;
  productId: number;
  title: string;
  artistName: string;
  thumbnailUrl?: string;
  price: number;
  productStatus: ProductStatus;
  stockQuantity: number;
  quantity: number;
  subtotal: number;
}

export interface Cart {
  cartId?: number;
  items: CartItem[];
  totalAmount: number;
}

export interface CartItemAddRequest {
  productId: number;
  quantity: number;
}

export interface CartItemQuantityUpdateRequest {
  quantity: number;
}
