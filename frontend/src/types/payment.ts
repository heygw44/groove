export type PaymentStatus = 'READY' | 'DONE' | 'CANCELED' | 'FAILED';

export interface PaymentConfirmRequest {
  paymentKey: string;
  orderId: string;
  amount: number;
}

export interface PaymentConfirmResponse {
  paymentId: number;
  orderId: number;
  orderNumber: string;
  status: PaymentStatus;
  method?: string;
  amount: number;
  approvedAt?: string;
}

/** 주문 상세에 포함되는 결제 정보. 승인 이력이 있는 결제(DONE/CANCELED)만 내려온다. */
export interface OrderPayment {
  paymentId: number;
  method: string;
  status: PaymentStatus;
  amount: number;
  approvedAt: string;
  canceledAt?: string;
}
