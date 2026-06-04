/**
 * 쿠폰 발급 Rate Limit — POST /coupons/{id}/issue 회원당 분당 한도. RateLimitFilter 가 Security 보다
 * 먼저 실행되므로 JWT 를 직접 디코드해 memberId 로 키잉한다(토큰 부재 시 IP 폴백).
 */
package com.groove.coupon.api.ratelimit;
