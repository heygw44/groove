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
