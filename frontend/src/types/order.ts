export type OrderStatus =
  'PENDING' | 'PAID' | 'PREPARING' | 'SHIPPED' | 'DELIVERED' | 'CANCELED' | 'REFUNDED';

export interface OrderCreateRequest {
  cartItemIds?: number[];
  productId?: number;
  quantity?: number;
  addressId: number;
  memberCouponId: number | null;
}

export interface OrderCreateResponse {
  orderId: number;
  orderNumber: string;
  totalAmount: number;
  discountAmount: number;
  finalAmount: number;
  couponName?: string;
}

export interface OrderItem {
  productId: number;
  productName: string;
  price: number;
  quantity: number;
  lineAmount: number;
}

export interface ShippingAddress {
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2?: string;
}

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  finalAmount: number;
  discountAmount: number;
  couponName?: string;
  representativeProductName: string;
  itemCount: number;
  thumbnailUrl?: string;
  createdAt: string;
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  totalAmount: number;
  discountAmount: number;
  finalAmount: number;
  couponName?: string;
  items: OrderItem[];
  shippingAddress: ShippingAddress;
  createdAt: string;
  expiresAt: string;
  canceledAt?: string;
  cancelReason?: string;
}

export interface OrderListParams {
  status?: OrderStatus;
  page?: number;
  size?: number;
}

export interface OrderCancelRequest {
  reason?: string;
}

export interface AdminOrderSummary {
  id: number;
  orderNumber: string;
  memberEmail: string;
  status: OrderStatus;
  finalAmount: number;
  itemCount: number;
  createdAt: string;
}

export interface AdminOrderDetail {
  id: number;
  orderNumber: string;
  memberId: number;
  memberEmail: string;
  status: OrderStatus;
  totalAmount: number;
  discountAmount: number;
  finalAmount: number;
  couponName?: string;
  items: OrderItem[];
  shippingAddress: ShippingAddress;
  createdAt: string;
  expiresAt: string;
  canceledAt?: string;
  cancelReason?: string;
}

export interface AdminOrderListParams {
  status?: OrderStatus;
  keyword?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface AdminOrderStatusChangeRequest {
  status: OrderStatus;
}
