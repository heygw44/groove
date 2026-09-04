import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  AvailableCoupon,
  CouponIssueRequest,
  CouponIssueResponse,
  MemberCoupon,
  MemberCouponStatus,
} from '@/types/coupon';

export const issueCoupon = (payload: CouponIssueRequest) =>
  unwrap(client.post<ApiResponse<CouponIssueResponse>>('/coupons/issue', payload));

/** 페이징이 아니라 배열이다. issuedAt 내림차순으로 내려온다. */
export const getMyCoupons = (status?: MemberCouponStatus) =>
  unwrap(
    client.get<ApiResponse<MemberCoupon[]>>('/members/me/coupons', {
      params: status ? { status } : undefined,
    }),
  );

export const getAvailableCoupons = (orderAmount: number) =>
  unwrap(
    client.get<ApiResponse<AvailableCoupon[]>>('/coupons/available', {
      params: { orderAmount },
    }),
  );
