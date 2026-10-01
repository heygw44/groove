import type {
  CourierCode,
  OrderItemClaimStatus,
  OrderItemStatus,
  OrderStatus,
  OrderStatusGroup,
  ShippingAddress,
} from '@/types/order';
import type { PaymentStatus } from '@/types/payment';

/** 관리자 상품주문 목록(`GET /admin/order-items`) 행. 클레임·송장이 없으면 해당 키는 생략된다. */
export interface AdminOrderItemSummary {
  id: number;
  /** 소속 주문 id. 주문 상세(`GET /admin/orders/{id}`) 조회에 쓴다. */
  orderId: number;
  productOrderNumber: string;
  orderNumber: string;
  memberEmail: string;
  productName: string;
  quantity: number;
  status: OrderItemStatus;
  claimStatus?: OrderItemClaimStatus;
  courierCode?: CourierCode;
  trackingNumber?: string;
  /** 가상계좌 결제 주문. 판매취소에 구매자 환불계좌가 필요해 관리자가 직접 취소할 수 없다. */
  virtualAccountPayment: boolean;
  createdAt: string;
}

/** 관리자 주문 상세(`GET /admin/orders/{id}`)의 상품주문 행. 클레임이 없으면 claimStatus 키는 생략된다. */
export interface AdminOrderDetailItem {
  productId: number;
  productName: string;
  price: number;
  quantity: number;
  lineAmount: number;
  thumbnailUrl: string | null;
  productOrderNumber: string;
  status: OrderItemStatus;
  claimStatus?: OrderItemClaimStatus;
}

/** 관리자 주문 상세. 주문 단위 상태는 결제 생애주기(PENDING/PAID/CANCELED)만 나타낸다. */
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
  items: AdminOrderDetailItem[];
  shippingAddress: ShippingAddress;
  createdAt: string;
  expiresAt: string;
  canceledAt?: string;
  cancelReason?: string;
  paymentStatus?: PaymentStatus;
}

export interface AdminOrderItemListParams {
  statusGroup?: OrderStatusGroup;
  keyword?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export type AdminOrderItemCountParams = Omit<AdminOrderItemListParams, 'page' | 'size'>;

export interface AdminOrderItemCount {
  totalElements: number;
}

/** 일괄 발주확인·발송·배송완료 결과. 상태가 맞지 않거나 진행 중 클레임이 있는 건은 skipped 로 센다. */
export interface AdminOrderItemBulkResult {
  processed: number;
  skipped: number;
}

export interface AdminOrderItemConfirmRequest {
  orderItemIds: number[];
}

export interface AdminOrderItemDeliverRequest {
  orderItemIds: number[];
}

export interface AdminOrderItemShipEntry {
  orderItemId: number;
  courierCode: CourierCode;
  /** 서버 검증: 공백 불가, 최대 50자. */
  trackingNumber: string;
}

export interface AdminOrderItemShipRequest {
  items: AdminOrderItemShipEntry[];
}

/** 클레임 처리·판매취소 응답의 상품주문 행. */
export interface AdminOrderItemResult {
  productId: number;
  productName: string;
  price: number;
  quantity: number;
  lineAmount: number;
  thumbnailUrl: string | null;
  productOrderNumber: string;
  status: OrderItemStatus;
  claimStatus?: OrderItemClaimStatus;
}

export type OrderClaimType = 'CANCEL' | 'RETURN';

export type OrderClaimStatus = 'REQUESTED' | 'COLLECTING' | 'DONE' | 'REJECTED' | 'WITHDRAWN';

export interface AdminOrderClaimSummary {
  claimId: number;
  type: OrderClaimType;
  status: OrderClaimStatus;
  productOrderNumber: string;
  orderNumber: string;
  memberEmail: string;
  productName: string;
  reason?: string;
  requestedAt: string;
}

export interface AdminOrderClaimStatusCounts {
  requested: number;
  collecting: number;
  done: number;
  rejected: number;
  withdrawn: number;
  total: number;
}

export interface AdminOrderClaimCounts {
  cancel: AdminOrderClaimStatusCounts;
  returns: AdminOrderClaimStatusCounts;
}

export interface AdminOrderClaimListParams {
  type?: OrderClaimType;
  status?: OrderClaimStatus;
  page?: number;
  size?: number;
}

export interface AdminOrderClaimCompleteRequest {
  /** 반품 상품을 재고로 되돌릴지 여부. 필수. */
  restock: boolean;
}

export interface AdminOrderClaimRejectRequest {
  /** 필수, 최대 200자. */
  rejectReason: string;
}

export interface AdminOrderItemCancelRequest {
  /** 선택, 최대 200자. */
  reason?: string;
}
