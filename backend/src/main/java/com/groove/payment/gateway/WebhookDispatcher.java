package com.groove.payment.gateway;

/**
 * 결제 결과 웹훅 통보를 수신 측에 전달하는 포트.
 *
 * <p>{@link com.groove.payment.gateway.mock.MockWebhookSimulator} 가 비동기로 이 포트를 호출한다.
 * 구현체는 {@code PaymentWebhookHandler}(#W7-4) — 서명을 검증한 뒤 {@code PaymentCallbackService} 로
 * Payment/Order 상태를 갱신하고 실패 시 보상 트랜잭션(재고 복원)을 수행한다. HTTP {@code POST /api/v1/payments/webhook}
 * ({@code PaymentWebhookController})·폴링 스케줄러도 같은 처리 경로·같은 멱등성 키를 공유한다.
 */
@FunctionalInterface
public interface WebhookDispatcher {

    void dispatch(WebhookNotification notification);
}
