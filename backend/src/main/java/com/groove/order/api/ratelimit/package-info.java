/**
 * 주문 Rate Limit — POST /orders/{orderNumber}/guest-lookup IP당 분당 한도(API.md §3.5).
 * 게스트 조회는 비인증이라 IP 로 키잉해 orderNumber+email 무차별 대입 시도량을 억제한다.
 */
package com.groove.order.api.ratelimit;
