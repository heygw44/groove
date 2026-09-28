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

export const createOrder = (payload: OrderCreateRequest, idempotencyKey: string) =>
  unwrap(
    client.post<ApiResponse<OrderCreateResponse>>('/orders', payload, {
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  );

/** 주문서에서 배송지만 바뀌었을 때 쓴다. 상품·쿠폰이 같은 PENDING 주문에만 적용된다. */
export const updateOrderShippingAddress = (orderId: number, addressId: number) =>
  unwrap(
    client.patch<ApiResponse<OrderDetail>>(`/orders/${orderId}/shipping-address`, { addressId }),
  );

export const getOrders = (params: OrderListParams) =>
  unwrap(client.get<ApiResponse<PageResponse<OrderSummary>>>('/orders', { params }));

export const getOrder = (orderId: number) =>
  unwrap(client.get<ApiResponse<OrderDetail>>(`/orders/${orderId}`));

export const cancelOrder = (orderId: number, payload?: OrderCancelRequest) =>
  unwrap(client.post<ApiResponse<OrderDetail>>(`/orders/${orderId}/cancel`, payload));
