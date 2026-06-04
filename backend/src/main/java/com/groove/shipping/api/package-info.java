/**
 * 배송 REST 진입점 — {@code GET /api/v1/shippings/{trackingNumber}} (Public, 운송장 번호로 조회).
 *
 * <p>비트랜잭션 — {@code ShippingService} 가 트랜잭션 경계를 갖는다. 응답에는 수령인/주소 일부만 노출하고
 * 연락처·우편번호는 내리지 않는다(공개 엔드포인트).
 */
package com.groove.shipping.api;
