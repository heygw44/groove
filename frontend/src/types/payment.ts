export type PaymentStatus =
  | 'READY'
  | 'DONE'
  | 'PARTIAL_CANCELED'
  | 'CANCELED'
  | 'FAILED'
  | 'UNKNOWN'
  | 'CANCEL_REQUESTED'
  | 'WAITING_FOR_DEPOSIT';

export interface VirtualAccount {
  bankCode: string;
  accountNumber: string;
  customerName: string | null;
  dueDate: string;
}

export type PaymentMethodOption = 'CARD' | 'TOSSPAY' | 'NAVERPAY' | 'KAKAOPAY' | 'VIRTUAL_ACCOUNT';

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
  easyPayProvider: string | null;
  virtualAccount: VirtualAccount | null;
}

/** 주문 상세에 포함되는 결제 정보. 승인 이력이 있는 결제(DONE/PARTIAL_CANCELED/WAITING_FOR_DEPOSIT/CANCEL_REQUESTED/CANCELED)만 내려온다. */
export interface OrderPayment {
  paymentId: number;
  method: string;
  status: PaymentStatus;
  amount: number;
  approvedAt: string;
  canceledAt?: string;
  easyPayProvider: string | null;
  virtualAccount: VirtualAccount | null;
}
