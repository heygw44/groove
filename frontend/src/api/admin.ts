import { client, unwrap } from '@/api/client';
import type { AdminAuditLog, AdminAuditLogListParams } from '@/types/adminAuditLog';
import type {
  AdminMemberDetail,
  AdminMemberListParams,
  AdminMemberStatusChangeRequest,
  AdminMemberSummary,
} from '@/types/adminMember';
import type {
  AdminStatsSummary,
  DailySales,
  LimitedDropStats,
  PopularProduct,
  PopularProductParams,
  StatsPeriodParams,
} from '@/types/adminStats';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  AdminCouponCreateRequest,
  AdminCouponListParams,
  AdminCouponResponse,
  AdminCouponSummary,
  AdminCouponUpdateRequest,
} from '@/types/coupon';
import type {
  AdminLimitedDrop,
  AdminLimitedDropCreateRequest,
  AdminLimitedDropDetail,
  AdminLimitedDropListParams,
  AdminLimitedDropSummary,
  AdminLimitedDropUpdateRequest,
} from '@/types/limitedDrop';
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

export const getAdminLimitedDrops = (params: AdminLimitedDropListParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<AdminLimitedDropSummary>>>('/admin/limited-drops', {
      params,
    }),
  );

export const getAdminLimitedDrop = (id: number) =>
  unwrap(client.get<ApiResponse<AdminLimitedDropDetail>>(`/admin/limited-drops/${id}`));

export const createAdminLimitedDrop = (payload: AdminLimitedDropCreateRequest) =>
  unwrap(client.post<ApiResponse<AdminLimitedDrop>>('/admin/limited-drops', payload));

export const updateAdminLimitedDrop = (id: number, payload: AdminLimitedDropUpdateRequest) =>
  unwrap(client.patch<ApiResponse<AdminLimitedDrop>>(`/admin/limited-drops/${id}`, payload));

export const openAdminLimitedDrop = (id: number) =>
  unwrap(client.patch<ApiResponse<AdminLimitedDrop>>(`/admin/limited-drops/${id}/open`));

export const closeAdminLimitedDrop = (id: number) =>
  unwrap(client.patch<ApiResponse<AdminLimitedDrop>>(`/admin/limited-drops/${id}/close`));

export const getAdminStatsSummary = () =>
  unwrap(client.get<ApiResponse<AdminStatsSummary>>('/admin/stats/summary'));

export const getAdminDailySales = (params: StatsPeriodParams) =>
  unwrap(
    client.get<ApiResponse<DailySales[]>>('/admin/stats/daily-sales', {
      params,
    }),
  );

export const getAdminPopularProducts = (params: PopularProductParams) =>
  unwrap(
    client.get<ApiResponse<PopularProduct[]>>('/admin/stats/popular-products', {
      params,
    }),
  );

export const getAdminLimitedDropStats = () =>
  unwrap(client.get<ApiResponse<LimitedDropStats[]>>('/admin/stats/limited-drops'));

export const getAdminMembers = (params: AdminMemberListParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<AdminMemberSummary>>>('/admin/members', {
      params,
    }),
  );

export const getAdminMember = (id: number) =>
  unwrap(client.get<ApiResponse<AdminMemberDetail>>(`/admin/members/${id}`));

export const changeAdminMemberStatus = (id: number, payload: AdminMemberStatusChangeRequest) =>
  unwrap(client.patch<ApiResponse<AdminMemberDetail>>(`/admin/members/${id}/status`, payload));

export const getAdminAuditLogs = (params: AdminAuditLogListParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<AdminAuditLog>>>('/admin/audit-logs', {
      params,
    }),
  );
