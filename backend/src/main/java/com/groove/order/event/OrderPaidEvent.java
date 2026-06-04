package com.groove.order.event;

/**
 * 주문 결제 완료 이벤트 (#W7-4 발행, #W7-5 구독).
 *
 * <p>결제 결과 콜백 트랜잭션 안에서 {@code PaymentCallbackService} 가 발행한다. 본 이슈(#W7-4)는 발행
 * 지점까지만 연결하며, AFTER_COMMIT 리스너에서 수행할 후속 처리(배송 생성 등)는 #W7-5 범위다.
 *
 * @param orderId     주문 식별자
 * @param orderNumber 주문 외부 식별자
 * @param memberId    회원 식별자 (게스트 주문이면 {@code null})
 * @param paymentId   결제 식별자
 */
public record OrderPaidEvent(Long orderId, String orderNumber, Long memberId, Long paymentId) {
}
