export type AdminAuditAction =
  | 'PRODUCT_CREATE'
  | 'PRODUCT_UPDATE'
  | 'PRODUCT_HIDE'
  | 'PRODUCT_RESTORE'
  | 'ORDER_STATUS_CHANGE'
  | 'COUPON_CREATE'
  | 'COUPON_UPDATE'
  | 'COUPON_DISABLE'
  | 'LIMITED_DROP_CREATE'
  | 'LIMITED_DROP_UPDATE'
  | 'LIMITED_DROP_OPEN'
  | 'LIMITED_DROP_CLOSE'
  | 'MEMBER_STATUS_CHANGE'
  | 'PAYMENT_CANCEL'
  | 'STOCK_ADJUST';

export type AdminAuditTargetType = 'PRODUCT' | 'ORDER' | 'COUPON' | 'LIMITED_DROP' | 'MEMBER' | 'PAYMENT';

export interface AdminAuditLog {
  id: number;
  adminId: number;
  adminNickname: string;
  action: AdminAuditAction;
  targetType: AdminAuditTargetType;
  targetId: number;
  detail: string;
  ipAddress?: string;
  createdAt: string;
}

export interface AdminAuditLogListParams {
  action?: AdminAuditAction;
  targetType?: AdminAuditTargetType;
  adminId?: number;
  from?: string;
  to?: string;
  page: number;
  size?: number;
}
