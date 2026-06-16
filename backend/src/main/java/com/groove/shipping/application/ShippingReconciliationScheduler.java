package com.groove.shipping.application;

import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 배송 생성 보충(reconciliation) 스케줄러 — 결제는 PAID 로 커밋됐지만 배송이 없는 "고아 주문"을 주기적으로 스캔해 보충한다.
 *
 * <p>대상은 paid_at 이 now - groove.shipping.reconciliation.min-age 이전인 PAID 주문이다.
 * 보충은 아웃박스 컨슈머와 동일한 ShippingProvisioner 를 호출하므로 existsByOrderId + uk_shipping_order
 * 중복 방어로 재시도가 안전하다. 건별 try/catch 로 격리하고(다음 주기 재시도), 한 주기 처리량은 .batch-size 로 제한한다.
 *
 * <p>실행 주기/초기 지연은 groove.shipping.reconciliation.{interval,initial-delay}, 대상 최소 경과 시간은
 * .min-age, 주기당 처리 상한은 .batch-size.
 */
@Component
public class ShippingReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShippingReconciliationScheduler.class);

    private final OrderRepository orderRepository;
    private final ShippingProvisioner provisioner;
    private final Clock clock;
    private final Duration minAge;
    private final Limit batchLimit;

    public ShippingReconciliationScheduler(OrderRepository orderRepository,
                                           ShippingProvisioner provisioner,
                                           Clock clock,
                                           @Value("${groove.shipping.reconciliation.min-age:PT2M}") Duration minAge,
                                           @Value("${groove.shipping.reconciliation.batch-size:200}") int batchSize) {
        this.orderRepository = orderRepository;
        this.provisioner = provisioner;
        this.clock = clock;
        this.minAge = Objects.requireNonNull(minAge, "minAge");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("groove.shipping.reconciliation.batch-size 는 양수여야 합니다: " + batchSize);
        }
        this.batchLimit = Limit.of(batchSize);
    }

    @Scheduled(
            fixedDelayString = "${groove.shipping.reconciliation.interval:PT5M}",
            initialDelayString = "${groove.shipping.reconciliation.initial-delay:PT5M}")
    public void reconcileOrphanedOrders() {
        Instant cutoff = clock.instant().minus(minAge);
        List<OrderRepository.OrderNumberView> orphans =
                orderRepository.findByStatusAndPaidAtBeforeOrderByPaidAtAsc(OrderStatus.PAID, cutoff, batchLimit);
        if (orphans.isEmpty()) {
            return;
        }
        log.debug("배송 보충 대상 {}건 (cutoff={}, limit={})", orphans.size(), cutoff, batchLimit.max());
        for (OrderRepository.OrderNumberView order : orphans) {
            provisionOne(order);
        }
    }

    private void provisionOne(OrderRepository.OrderNumberView order) {
        try {
            if (provisioner.provisionForOrder(order.getId(), order.getOrderNumber())) {
                log.info("배송 보충: order={}", order.getOrderNumber());
            }
        } catch (DataIntegrityViolationException e) {
            // 다른 트랜잭션이 먼저 배송을 만든 경합 — 이미 보충됨(정상)이므로 INFO 흡수.
            log.info("배송 보충 건너뜀: order={} 이미 존재(릴레이와 경합)", order.getOrderNumber());
        } catch (RuntimeException e) {
            log.warn("배송 보충 실패: order={} — 다음 주기에 재시도", order.getOrderNumber(), e);
        }
    }
}
