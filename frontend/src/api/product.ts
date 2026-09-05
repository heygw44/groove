import { client, unwrap } from '@/api/client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type { ProductDetail, ProductListParams, ProductSummary } from '@/types/product';

export const getProducts = (params: ProductListParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<ProductSummary>>>('/products', {
      params,
    }),
  );

export const getProduct = (id: number) =>
  unwrap(client.get<ApiResponse<ProductDetail>>(`/products/${id}`));
