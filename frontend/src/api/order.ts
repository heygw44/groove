import { client, unwrap } from '@/api/client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  OrderCancelRequest,
  OrderCreateRequest,
  OrderCreateResponse,
  OrderDetail,
  OrderListParams,
  OrderSummary,
} from '@/types/order';

export const createOrder = (payload: OrderCreateRequest) =>
  unwrap(client.post<ApiResponse<OrderCreateResponse>>('/orders', payload));

export const getOrders = (params: OrderListParams) =>
  unwrap(client.get<ApiResponse<PageResponse<OrderSummary>>>('/orders', { params }));

export const getOrder = (orderId: number) =>
  unwrap(client.get<ApiResponse<OrderDetail>>(`/orders/${orderId}`));

export const cancelOrder = (orderId: number, payload?: OrderCancelRequest) =>
  unwrap(client.post<ApiResponse<OrderDetail>>(`/orders/${orderId}/cancel`, payload));
