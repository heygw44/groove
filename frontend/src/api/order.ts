import { client, unwrap } from '@/api/client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  OrderCancelRequest,
  OrderCreateRequest,
  OrderCreateResponse,
  OrderDetail,
  OrderItem,
  OrderListParams,
  OrderReturnRequest,
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

/** 결제완료 상품은 즉시 취소·부분환불, 배송준비 상품은 취소 요청(클레임)만 남긴다. */
export const cancelOrderItem = (orderId: number, itemId: number, payload?: OrderCancelRequest) =>
  unwrap(client.post<ApiResponse<OrderItem>>(`/orders/${orderId}/items/${itemId}/cancel`, payload));

export const returnOrderItem = (orderId: number, itemId: number, payload?: OrderReturnRequest) =>
  unwrap(client.post<ApiResponse<OrderItem>>(`/orders/${orderId}/items/${itemId}/return`, payload));

export const withdrawOrderClaim = (claimId: number) =>
  unwrap(client.post<ApiResponse<OrderItem>>(`/order-claims/${claimId}/withdraw`));

export const confirmOrderItem = (orderId: number, itemId: number) =>
  unwrap(client.post<ApiResponse<OrderDetail>>(`/orders/${orderId}/items/${itemId}/confirm`));
