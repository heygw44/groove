export type DiscountType = 'FIXED' | 'RATE';

export type MemberCouponStatus = 'usable' | 'used' | 'expired';

export interface CouponDiscount {
  discountType: DiscountType;
  discountValue: number;
  minOrderAmount: number;
  maxDiscountAmount?: number;
}

export interface MemberCoupon extends CouponDiscount {
  memberCouponId: number;
  couponId: number;
  couponCode: string;
  couponName: string;
  expiresAt: string;
  used: boolean;
  expired: boolean;
  issuedAt: string;
  usedAt?: string;
}

export interface CouponIssueRequest {
  code: string;
}

export interface CouponIssueResponse {
  memberCouponId: number;
  couponCode: string;
  couponName: string;
  discountType: DiscountType;
  discountValue: number;
  expiresAt: string;
}

export interface AvailableCoupon extends CouponDiscount {
  memberCouponId: number;
  couponCode: string;
  couponName: string;
  expiresAt: string;
  expectedDiscount: number;
}

export type CouponStatus = 'ACTIVE' | 'DISABLED';

/** 서버가 내려주는 status 는 발급 중지 여부일 뿐, 만료는 화면이 expiresAt 으로 따로 판단한다. */
export type AdminCouponDisplayStatus = 'ACTIVE' | 'EXPIRED' | 'DISABLED';

export interface AdminCouponSummary extends CouponDiscount {
  id: number;
  code: string;
  name: string;
  totalQuantity?: number;
  issuedCount: number;
  usedCount: number;
  expiresAt: string;
  status: CouponStatus;
  createdAt: string;
}

export interface AdminCouponResponse extends CouponDiscount {
  id: number;
  code: string;
  name: string;
  totalQuantity?: number;
  issuedCount: number;
  expiresAt: string;
  status: CouponStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AdminCouponCreateRequest {
  code: string;
  name: string;
  discountType: DiscountType;
  discountValue: number;
  minOrderAmount?: number;
  maxDiscountAmount?: number;
  totalQuantity?: number;
  expiresAt: string;
}

export interface AdminCouponUpdateRequest {
  name?: string;
  discountType?: DiscountType;
  discountValue?: number;
  minOrderAmount?: number;
  /** null 이면 해제(상한 없음). */
  maxDiscountAmount?: number | null;
  /** null 이면 해제(무제한). */
  totalQuantity?: number | null;
  expiresAt?: string;
  status?: CouponStatus;
}

export interface AdminCouponListParams {
  status?: CouponStatus;
  page: number;
  size: number;
}
