/**
 * 주문 REST 진입점. 회원/게스트 분기는 {@code @AuthenticationPrincipal(required = false)} 로
 * 토큰 유무를 분리하고, 게스트 경로는 SecurityConfig 의 POST 매처에서 명시적으로 풀린다.
 */
package com.groove.order.api;
