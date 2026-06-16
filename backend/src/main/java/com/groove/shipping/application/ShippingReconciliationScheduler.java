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
 * 배송 생성 보충(reconciliation) 스케줄러 (이슈 #169) — 결제는 PAID 로 커밋됐지만 배송이 없는 "고아 주문"을
 * 주기적으로 스캔해 보충한다.
 *
 * <p>2차 방어선(#237): 결제 완료 후속 배송 생성의 1차 durable 경로는 이제 트랜잭셔널 아웃박스
 * ({@code OutboxRelayScheduler} → {@code OrderPaidOutboxHandler})다 — PAID 와 같은 트랜잭션에 이벤트가
 * 기록돼 유실되지 않고 at-least-once 로 발행된다. 이 스케줄러는 컨슈머/릴레이 버그나 마이그레이션 이전 PAID
 * 데이터 같은 예외 상황을 커버하는 보조 안전망으로 남긴다(검증 후 후속 이슈에서 제거 가능). 대상은 {@code paid_at}
 * 이 {@code now - groove.shipping.reconciliation.min-age} 이전인 PAID 주문이다 — 갓 결제된 주문은 릴레이가 곧
 * 처리하므로 제외한다. 보충은 아웃박스 컨슈머와 동일한 {@link ShippingProvisioner} 를 호출하므로
 * {@code existsByOrderId} + {@code uk_shipping_order} 중복 방어가 그대로 적용돼 재시도가 안전하다. 한 건의 실패가
 * 배치 전체를 막지 않도록 건별로 격리하고(다음 주기에 재시도), 스케줄러 스레드 밖으로 예외를 흘리지 않는다. 한 주기
 * 처리량은 {@code .batch-size} 로 제한한다(메모리 바운드).
 *
 * <p>정상 흐름에선 배송 생성과 함께 주문이 PREPARING 으로 전진하므로 대상 집합(PAID 잔류)은 보통 비어 있다.
 * 실행 주기/초기 지연은 {@code groove.shipping.reconciliation.{interval,initial-delay}}, 대상 최소 경과 시간은
 * {@code .min-age}, 주기당 처리 상한은 {@code .batch-size}. 전역 {@code @EnableScheduling} 은
 * {@code common.scheduling.SchedulingConfig} 에 있다 — 자체 {@code @EnableScheduling} 을 두지 않는다.
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
                log.info("배송 보충: order={} — 아웃박스 릴레이 누락분 복구", order.getOrderNumber());
            }
        } catch (DataIntegrityViolationException e) {
            // 아웃박스 릴레이와 경합해 다른 트랜잭션이 먼저 배송을 만든 경우 — 이미 보충됨(정상). 릴레이와
            // 동일하게 INFO 흡수해 정상 경합을 '실패' 알람으로 오인하지 않는다. 다음 주기엔 existsByOrderId 가 걸러낸다.
            log.info("배송 보충 건너뜀: order={} 이미 존재(릴레이와 경합)", order.getOrderNumber());
        } catch (RuntimeException e) {
            log.warn("배송 보충 실패: order={} — 다음 주기에 재시도", order.getOrderNumber(), e);
        }
    }
}
