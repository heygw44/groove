package com.groove.payment.gateway;

/**
 * 결제 결과 웹훅 통보를 수신 측에 전달하는 포트.
 *
 * <p>{@link com.groove.payment.gateway.mock.MockWebhookSimulator} 가 비동기로 이 포트를 호출한다.
 * #W7-1 에서는 통보를 로그만 남기는 기본 구현({@link com.groove.payment.gateway.mock.LoggingWebhookDispatcher})
 * 을 제공하고, 실제 웹훅 수신 처리(서명 검증 · Payment/Order 상태 갱신 · 보상 트랜잭션)는
 * #W7-4 에서 이 인터페이스의 구현체로 교체한다.
 */
@FunctionalInterface
public interface WebhookDispatcher {

    void dispatch(WebhookNotification notification);
}
