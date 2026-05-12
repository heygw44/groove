/**
 * 주문 도메인 이벤트.
 *
 * <p>{@link com.groove.order.event.OrderPaidEvent} — 결제 완료 시 발행(#W7-4),
 * {@link com.groove.order.event.OrderPaidEventListener} 가 AFTER_COMMIT 으로 수신(#W7-5 골격).
 * 배송 생성 등 실제 후속 처리는 #W7-6(shipping) 에서 별도 리스너로 연결한다.
 */
package com.groove.order.event;
