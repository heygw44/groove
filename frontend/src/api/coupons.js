import client from './client'

// 쿠폰 API (회원 전용). 체크아웃 쿠폰 선택은 보유 쿠폰 중 사용 가능(ISSUED)만 필터해 조회한다.

/**
 * 내 보유 쿠폰 목록. → PageResponse<MemberCouponResponse>.
 * @param {{status?:string, page?:number, size?:number}} [params] 체크아웃은 {status:'ISSUED', size:100}.
 */
export function myCoupons(params = {}) {
  return client.get('/me/coupons', { params }).then((res) => res.data)
}
