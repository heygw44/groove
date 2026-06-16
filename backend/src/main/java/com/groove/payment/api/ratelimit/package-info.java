/**
 * 결제 Rate Limit — POST /payments 회원당 분당 5회. JWT 를 직접 디코드해 memberId 로 키잉한다(게스트·토큰 부재 시 IP 폴백).
 */
package com.groove.payment.api.ratelimit;
