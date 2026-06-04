import client from './client'
import { randomUuid } from '@/lib/uuid'

// 쿠폰 API. 목록(GET /coupons)은 public, 발급(POST issue)·내 쿠폰(GET /members/me/coupons)은 회원 전용.

/**
 * 발급 가능 쿠폰 목록(공개). → PageResponse<CouponResponse>.
 * @param {{page?:number, size?:number, sort?:string}} [params]
 */
export function listCoupons(params = {}) {
  return client.get('/coupons', { params, auth: false }).then((res) => res.data)
}

/**
 * 선착순 쿠폰 발급(현재 로그인 회원). 성공 시 MemberCouponResponse.
 * idempotent:true 면 인터셉터가 Idempotency-Key 를 생성하고 401 재시도에도 동일 키를 보존한다(서버 dedup 유지).
 */
export function issueCoupon(couponId) {
  return client
    .post(`/coupons/${couponId}/issue`, null, { idempotent: true })
    .then((res) => res.data)
}

/**
 * 데모 전용 발급 — 계정별 accessToken 을 명시 첨부한다(동시성 라이브 데모의 병렬 발급).
 * 인터셉터의 전역 Bearer 자동 첨부 경로를 쓰지 않으려고 auth:false + 명시 헤더를 쓴다.
 * → 전역 로그인 세션을 건드리지 않고, 401 자동 refresh 도 발화하지 않는다.
 */
export function issueCouponWithToken(couponId, accessToken) {
  return client
    .post(`/coupons/${couponId}/issue`, null, {
      auth: false,
      headers: { Authorization: `Bearer ${accessToken}`, 'Idempotency-Key': randomUuid() },
    })
    .then((res) => res.data)
}

/**
 * 내 보유 쿠폰 목록. → PageResponse<MemberCouponResponse>.
 * @param {{status?:string, page?:number, size?:number}} [params] 체크아웃은 {status:'ISSUED', size:100}.
 */
export function myCoupons(params = {}) {
  return client.get('/members/me/coupons', { params }).then((res) => res.data)
}
