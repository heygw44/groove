/**
 * 배송 application 계층 — 결제 완료 이벤트 구독(배송 생성), 운송장 조회, 시연용 자동 진행 스케줄러.
 *
 * <p>{@code ShippingCreationListener} 는 {@code OrderPaidEvent} 의 AFTER_COMMIT 리스너로 자기
 * 트랜잭션({@code REQUIRES_NEW})에서 배송 행을 만든다. {@code ShippingProgressScheduler} 는 전역
 * {@code @EnableScheduling}({@code common.scheduling.SchedulingConfig})을 재사용하며, 상태 전이는
 * {@code ShippingService} 의 트랜잭션 메서드로 위임한다. 운송장 발급은 {@code TrackingNumberGenerator} 로 추상화한다.
 */
package com.groove.shipping.application;
