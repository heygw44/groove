package com.groove.shipping.application;

import com.groove.order.event.OrderPaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 완료 시 배송을 자동 생성하는 리스너 (#W7-6) — OrderPaidEvent 를 AFTER_COMMIT 으로 받아 ShippingProvisioner 로 위임해
 * 배송을 PREPARING 상태로 만들고 운송장 번호를 발급한다.
 *
 * AFTER_COMMIT 인 이유: 이벤트는 결제 결과 적용 트랜잭션 안에서 발행된다(=PaymentCallbackService.applyResult()).
 * AFTER_COMMIT 바인딩이면 그 트랜잭션 커밋 뒤에만 실행되므로 "확정되지 않은 결제"에 배송이 새지 않는다. 실제 DB 쓰기는
 * provisionForOrder 의 REQUIRES_NEW 트랜잭션이 담당한다 — AFTER_COMMIT 시점에는 활성 트랜잭션이 없기 때문.
 *
 * 실패 격리와 보충: AFTER_COMMIT 리스너의 예외는 트랜잭션 동기화 과정에서 흡수돼 호출자(결제 콜백)로 전파되지 않으므로 여기서
 * 잡아 로그로 남긴다. DataIntegrityViolationException(중복 이벤트/경합)은 "이미 생성됨"으로 흡수하고, 그 밖의 일시 실패는
 * 주문이 PAID 인데 배송이 없는 상태로 남지만 ShippingReconciliationScheduler 가 주기적으로 스캔해 보충하므로 영구 고아가 되지
 * 않는다(이슈 #169).
 */
@Component
public class ShippingCreationListener {

    private static final Logger log = LoggerFactory.getLogger(ShippingCreationListener.class);

    private final ShippingProvisioner provisioner;

    public ShippingCreationListener(ShippingProvisioner provisioner) {
        this.provisioner = provisioner;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            provisioner.provisionForOrder(event.orderId(), event.orderNumber());
        } catch (DataIntegrityViolationException e) {
            log.info("배송 생성 건너뜀: order={} 이미 존재(중복 이벤트/경합)", event.orderNumber());
        } catch (RuntimeException e) {
            // 실패 시점에 주문 상태를 확인하지 않으므로(관리자/환불로 PAID 가 아닐 수도 있음) PAID 를 단언하지 않는다.
            log.error("배송 생성 실패: order={} — 배송 미생성, 주문이 PAID 면 다음 reconciliation 에서 보충",
                    event.orderNumber(), e);
        }
    }
}
