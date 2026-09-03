import { client, unwrap } from '@/api/client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  AdminProductCreateRequest,
  AdminProductListParams,
  AdminProductResponse,
  AdminProductSummary,
  AdminProductUpdateRequest,
  StockAdjustRequest,
  StockAdjustResponse,
} from '@/types/product';

export const getAdminProducts = (params: AdminProductListParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<AdminProductSummary>>>('/admin/products', {
      params,
    }),
  );

export const createAdminProduct = (payload: AdminProductCreateRequest) =>
  unwrap(client.post<ApiResponse<AdminProductResponse>>('/admin/products', payload));

export const updateAdminProduct = (id: number, payload: AdminProductUpdateRequest) =>
  unwrap(client.patch<ApiResponse<AdminProductResponse>>(`/admin/products/${id}`, payload));

export const hideAdminProduct = (id: number) =>
  unwrap(client.delete<ApiResponse<void>>(`/admin/products/${id}`));

export const getAdminProduct = (id: number) =>
  unwrap(client.get<ApiResponse<AdminProductResponse>>(`/admin/products/${id}`));

export const restoreAdminProduct = (id: number) =>
  unwrap(client.patch<ApiResponse<AdminProductResponse>>(`/admin/products/${id}/restore`));

export const adjustStock = (id: number, payload: StockAdjustRequest) =>
  unwrap(
    client.patch<ApiResponse<StockAdjustResponse>>(`/admin/products/${id}/stock`, payload),
  );
