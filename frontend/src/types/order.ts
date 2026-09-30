import type { OrderPayment } from '@/types/payment';

export type OrderStatus = 'PENDING' | 'PAID' | 'CANCELED';

/** 상품주문(order_item) 단위 이행 상태. PAYMENT_PENDING 은 결제 전 내부 상태라 화면에 노출하지 않는다. */
export type OrderItemStatus =
  | 'PAYMENT_PENDING'
  | 'PAYMENT_WAITING'
  | 'PAID'
  | 'PREPARING'
  | 'SHIPPING'
  | 'DELIVERED'
  | 'PURCHASE_CONFIRMED'
  | 'CANCELED'
  | 'RETURNED'
  | 'CANCELED_BY_NOPAYMENT';

/** 상품주문에 걸린 진행 중·완료 클레임 파생 표시. 값이 없으면 클레임 없음. */
export type OrderItemClaimStatus =
  | 'CANCEL_REQUEST'
  | 'CANCEL_DONE'
  | 'CANCEL_REJECT'
  | 'RETURN_REQUEST'
  | 'COLLECTING'
  | 'RETURN_DONE'
  | 'RETURN_REJECT';

/** 서버 OrderItemActionPolicy 가 계산해 내려주는 상품주문 액션. */
export type OrderItemAction =
  | 'CANCEL'
  | 'CANCEL_REQUEST'
  | 'RETURN_REQUEST'
  | 'WITHDRAW_CLAIM'
  | 'TRACK'
  | 'CONFIRM'
  | 'WRITE_REVIEW';

/** 구매자 주문 목록 탭(`GET /orders?statusGroup=`). 생략하면 전체. */
export type OrderStatusGroup =
  | 'PAYMENT_WAITING'
  | 'PAID'
  | 'PREPARING'
  | 'SHIPPING'
  | 'DELIVERED'
  | 'PURCHASE_CONFIRMED'
  | 'CANCEL_RETURN';

/** 발송처리 시 서버가 검증하는 택배사 코드. 조회 URL 템플릿은 constants/couriers.ts. */
export type CourierCode = 'CJ' | 'HANJIN' | 'LOTTE' | 'EPOST' | 'LOGEN' | 'KDEXP';

/** 목록·상세 응답이 상품주문 행마다 공통으로 내려주는 이행 정보. */
export interface OrderItemFulfillment {
  productOrderNumber: string;
  status: OrderItemStatus;
  /** 값이 있을 때만(클레임 없으면 키 자체가 없다). */
  claimStatus?: OrderItemClaimStatus;
  /** 할인 반영된 결제 금액(discount_share 뺀 값). */
  paidAmount: number;
  courierCode?: CourierCode;
  /** 발송 전이면 생략. */
  trackingNumber?: string;
  availableActions: OrderItemAction[];
  /** 부분 환불이 결제사에 나갔지만 결과가 아직 확정되지 않은 동안 true. */
  refundInProgress: boolean;
}

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
  /** 결제 대기 만료 시각. 없으면 만료 판정 없이 이 주문을 계속 재사용한다. */
  expiresAt?: string;
}

export interface OrderItem extends OrderItemFulfillment {
  /** 상품주문(order_item) id. 취소·반품·구매확정 경로의 itemId. */
  id: number;
  /** 철회할 수 있는 진행 중 클레임 id. WITHDRAW_CLAIM 액션이 있을 때만 존재한다. */
  claimId?: number;
  productId: number;
  productName: string;
  price: number;
  quantity: number;
  lineAmount: number;
  thumbnailUrl: string | null;
  /** 배송완료 시각. 배송완료 전이면 생략. */
  deliveredAt?: string;
}

export interface OrderListItem extends OrderItemFulfillment {
  productId: number;
  productName: string;
  quantity: number;
  lineAmount: number;
  thumbnailUrl: string | null;
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
  /** 주문에 담긴 상품 행 전부. items[0]이 representativeProductName/thumbnailUrl과 같다. */
  items: OrderListItem[];
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
  /** 한정반 구매 주문에만 존재. 만료 취소로 LimitedPurchase 가 지워지면 재조회 시 사라질 수 있다. */
  limitedDropId?: number;
  /** 승인 이력이 있는 결제(DONE/CANCEL_REQUESTED/CANCELED)만 존재. */
  payment?: OrderPayment;
}

export interface OrderListParams {
  statusGroup?: OrderStatusGroup;
  page?: number;
  size?: number;
}

/** 입금 완료(PAID) 가상계좌 결제 취소 시 토스가 요구하는 환불계좌. */
export interface RefundAccount {
  bankCode: string;
  accountNumber: string;
  holderName: string;
}

export interface OrderCancelRequest {
  reason?: string;
  refundAccount?: RefundAccount;
}

export interface OrderReturnRequest {
  reason?: string;
  refundAccount?: RefundAccount;
}
