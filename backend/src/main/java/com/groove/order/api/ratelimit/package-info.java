/**
 * 주문 Rate Limit — POST /orders/{orderNumber}/guest-lookup IP당 분당 한도.
 * 게스트 조회는 비인증이라 IP 로 키잉한다.
 */
package com.groove.order.api.ratelimit;
