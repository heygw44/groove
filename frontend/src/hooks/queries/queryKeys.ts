import type { OrderListParams } from '@/types/order';
import type { AdminProductListParams, ProductListParams } from '@/types/product';
import type { WishlistListParams } from '@/types/wishlist';

export const memberKeys = {
  me: ['member', 'me'] as const,
};

export const addressKeys = {
  all: ['addresses'] as const,
};

export const cartKeys = {
  all: ['cart'] as const,
};

export const productKeys = {
  all: ['products'] as const,
  list: (params: ProductListParams) => ['products', params] as const,
  detail: (id: number) => ['product', id] as const,
};

export const adminProductKeys = {
  all: ['adminProducts'] as const,
  list: (params: AdminProductListParams) => ['adminProducts', params] as const,
  detail: (id: number) => ['adminProducts', 'detail', id] as const,
};

// detail 을 'order' 단수로 분리해 ['orders'] 무효화가 상세 캐시를 건드리지 않게 한다 (productKeys 와 같은 이유).
export const orderKeys = {
  all: ['orders'] as const,
  list: (params: OrderListParams) => ['orders', params] as const,
  detail: (id: number) => ['order', id] as const,
};

export const wishlistKeys = {
  all: ['wishlist'] as const,
  list: (params: WishlistListParams) => ['wishlist', params] as const,
};

export const referenceKeys = {
  genres: ['genres'] as const,
  labels: ['labels'] as const,
  artists: (keyword?: string) => ['artists', keyword] as const,
  artist: (id: number) => ['artist', id] as const,
};
