import { client, unwrap } from '@/api/client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  AdminCouponCreateRequest,
  AdminCouponListParams,
  AdminCouponResponse,
  AdminCouponSummary,
  AdminCouponUpdateRequest,
} from '@/types/coupon';
import type {
  AdminOrderDetail,
  AdminOrderListParams,
  AdminOrderStatusChangeRequest,
  AdminOrderSummary,
} from '@/types/order';
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
  unwrap(client.patch<ApiResponse<StockAdjustResponse>>(`/admin/products/${id}/stock`, payload));

export const getAdminOrders = (params: AdminOrderListParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<AdminOrderSummary>>>('/admin/orders', {
      params,
    }),
  );

export const getAdminOrder = (orderId: number) =>
  unwrap(client.get<ApiResponse<AdminOrderDetail>>(`/admin/orders/${orderId}`));

export const changeAdminOrderStatus = (orderId: number, payload: AdminOrderStatusChangeRequest) =>
  unwrap(client.patch<ApiResponse<AdminOrderDetail>>(`/admin/orders/${orderId}/status`, payload));

export const getAdminCoupons = (params: AdminCouponListParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<AdminCouponSummary>>>('/admin/coupons', {
      params,
    }),
  );

export const createAdminCoupon = (payload: AdminCouponCreateRequest) =>
  unwrap(client.post<ApiResponse<AdminCouponResponse>>('/admin/coupons', payload));

export const updateAdminCoupon = (id: number, payload: AdminCouponUpdateRequest) =>
  unwrap(client.patch<ApiResponse<AdminCouponResponse>>(`/admin/coupons/${id}`, payload));

export const disableAdminCoupon = async (id: number) => {
  await client.delete<ApiResponse<void>>(`/admin/coupons/${id}`);
};
