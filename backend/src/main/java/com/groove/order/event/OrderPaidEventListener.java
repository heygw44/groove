package com.groove.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link OrderPaidEvent} 의 트랜잭션 커밋 이후 처리 진입점 (#W7-5 골격).
 *
 * <h2>{@code AFTER_COMMIT} 인 이유</h2>
 * <p>이벤트는 {@code PaymentCallbackService.applyResult()} 의 결제 결과 적용 트랜잭션 안에서 발행된다.
 * {@link TransactionPhase#AFTER_COMMIT} 으로 바인딩하면 그 트랜잭션이 <em>커밋된 뒤에만</em> 같은 스레드에서
 * 실행되므로:
 * <ul>
 *   <li>결제 적용 트랜잭션이 롤백되면 — 어느 단계든 실패해 보상 트랜잭션 전체가 되돌려지면 — 이 리스너는 호출되지
 *       않는다. "확정되지 않은 결제"에 대한 후속 처리가 새는 일이 없다.</li>
 *   <li>이 리스너에서 예외가 나도 이미 커밋된 결제 트랜잭션을 되돌리지 못한다 — 결제 확정과 후속 처리(배송 생성 등)의
 *       실패가 서로 격리된다. 다만 그만큼 후속 처리 실패는 이 안에서 책임지고 처리/재시도해야 한다.</li>
 * </ul>
 *
 * <h2>범위</h2>
 * <p>본 이슈(#W7-5)는 발행 → AFTER_COMMIT 수신까지의 연결만 보장한다. 실제 후속 처리 — 결제 완료 시 배송
 * {@code PREPARING} 생성 + 운송장 번호 발급 — 은 #W7-6(shipping 도메인)에서 별도의 {@code @TransactionalEventListener}
 * 로 연결한다. 한 이벤트에 여러 리스너가 붙는 것은 정상이며, shipping 쪽 리스너가 추가돼도 이 클래스는 그대로 둔다
 * (감사 로그). #W7-6 의 리스너처럼 커밋 이후 DB 쓰기를 수행하는 리스너는 자체 트랜잭션이 필요하므로
 * {@code @Transactional(propagation = Propagation.REQUIRES_NEW)} 를 함께 붙여야 한다 — 이 골격은 쓰기가 없어 불필요하다.
 */
@Component
public class OrderPaidEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("주문 결제 완료 이벤트 수신(AFTER_COMMIT): order={}, paymentId={} — 배송 생성 등 후속 처리는 #W7-6 에서 연결",
                event.orderNumber(), event.paymentId());
    }
}
