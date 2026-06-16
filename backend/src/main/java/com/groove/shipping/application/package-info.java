/**
 * 배송 application 계층 — 결제 완료 이벤트 구독(배송 생성), 운송장 조회, 자동 진행 스케줄러.
 *
 * <p>OrderPaidOutboxHandler 는 OrderPaidEvent 아웃박스 컨슈머로 자기 트랜잭션
 * (REQUIRES_NEW, ShippingProvisioner)에서 배송 행을 만든다. ShippingProgressScheduler 의
 * 상태 전이는 ShippingService 의 트랜잭션 메서드로 위임한다. 운송장 발급은 TrackingNumberGenerator 로 추상화한다.
 */
package com.groove.shipping.application;
