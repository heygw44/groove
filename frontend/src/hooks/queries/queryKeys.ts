import type { AdminProductListParams, ProductListParams } from '@/types/product';

export const memberKeys = {
  me: ['member', 'me'] as const,
};

export const addressKeys = {
  all: ['addresses'] as const,
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

export const referenceKeys = {
  genres: ['genres'] as const,
  labels: ['labels'] as const,
  artists: (keyword?: string) => ['artists', keyword] as const,
};
