package com.groove.shipping.application;

import com.groove.order.domain.OrderRepository;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 주문/배송 PII 익명화 배치 스케줄러. 배송완료(ShippingStatus.DELIVERED)된 지 보존기간
 * (groove.privacy.order-anonymization.retention)이 지난 주문/배송의 수령인·주소·게스트 PII 를 주기적으로 마스킹한다.
 * 대상은 delivered_at 이 now - retention 이전이고 아직 익명화되지 않은(anonymized_at IS NULL) DELIVERED 배송이다.
 *
 * <p>경량 projection 으로 대상 id 를 조회하고, 건별로 OrderPiiAnonymizer(REQUIRES_NEW)를 호출한다.
 * 건별 try/catch 로 한 건 실패를 격리하고(다음 주기 재시도), 한 주기 처리량은 .batch-size 로 제한한다.
 *
 * <p>배송이 생성되지 않는 종착 주문(PENDING / PAYMENT_FAILED / 배송 생성 전 CANCELLED)의 PII 는
 * anonymizeTerminalNonShippingOrders() 가 주문 상태 + updated_at + 보존기간 기준으로 별도 익명화한다.
 */
@Component
public class OrderPiiAnonymizationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderPiiAnonymizationScheduler.class);

    private final ShippingRepository shippingRepository;
    private final OrderRepository orderRepository;
    private final OrderPiiAnonymizer anonymizer;
    private final Clock clock;
    private final Duration retention;
    private final Limit batchLimit;

    public OrderPiiAnonymizationScheduler(ShippingRepository shippingRepository,
                                          OrderRepository orderRepository,
                                          OrderPiiAnonymizer anonymizer,
                                          Clock clock,
                                          @Value("${groove.privacy.order-anonymization.retention:P90D}") Duration retention,
                                          @Value("${groove.privacy.order-anonymization.batch-size:200}") int batchSize) {
        this.shippingRepository = shippingRepository;
        this.orderRepository = orderRepository;
        this.anonymizer = anonymizer;
        this.clock = clock;
        this.retention = Objects.requireNonNull(retention, "retention");
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException(
                    "groove.privacy.order-anonymization.retention 은 양수여야 합니다: " + retention);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "groove.privacy.order-anonymization.batch-size 는 양수여야 합니다: " + batchSize);
        }
        this.batchLimit = Limit.of(batchSize);
    }

    @Scheduled(
            fixedDelayString = "${groove.privacy.order-anonymization.interval:PT1H}",
            initialDelayString = "${groove.privacy.order-anonymization.initial-delay:PT10M}")
    public void anonymizeDeliveredOrders() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(retention);
        List<ShippingRepository.ShippingIdView> targets =
                shippingRepository.findByStatusAndDeliveredAtBeforeAndAnonymizedAtIsNullOrderByDeliveredAtAsc(
                        ShippingStatus.DELIVERED, cutoff, batchLimit);
        if (targets.isEmpty()) {
            return;
        }
        log.debug("PII 익명화 대상 {}건 (cutoff={}, limit={})", targets.size(), cutoff, batchLimit.max());
        for (ShippingRepository.ShippingIdView target : targets) {
            anonymizeOne(target.getId(), now);
        }
    }

    private void anonymizeOne(Long shippingId, Instant now) {
        try {
            anonymizer.anonymizeForShipping(shippingId, now);
        } catch (RuntimeException e) {
            log.warn("PII 익명화 실패: shippingId={} — 다음 주기에 재시도", shippingId, e);
        }
    }

    /**
     * 배송이 없는 종착 주문 PII 익명화. 대상은 주문 상태(OrderPiiAnonymizer.TERMINAL_NON_SHIPPING_STATUSES)
     * + updated_at 기준이며, anonymized_at IS NULL 필터로 멱등하다. 건별 try/catch 로 격리한다.
     */
    @Scheduled(
            fixedDelayString = "${groove.privacy.order-anonymization.interval:PT1H}",
            initialDelayString = "${groove.privacy.order-anonymization.initial-delay:PT10M}")
    public void anonymizeTerminalNonShippingOrders() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(retention);
        List<OrderRepository.OrderNumberView> targets =
                orderRepository.findByStatusInAndAnonymizedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        OrderPiiAnonymizer.TERMINAL_NON_SHIPPING_STATUSES, cutoff, batchLimit);
        if (targets.isEmpty()) {
            return;
        }
        log.debug("비배송 종착 주문 PII 익명화 대상 {}건 (cutoff={}, limit={})", targets.size(), cutoff, batchLimit.max());
        for (OrderRepository.OrderNumberView target : targets) {
            anonymizeOneOrder(target.getId(), now);
        }
    }

    private void anonymizeOneOrder(Long orderId, Instant now) {
        try {
            anonymizer.anonymizeOrder(orderId, now);
        } catch (RuntimeException e) {
            log.warn("PII 익명화 실패: orderId={} — 다음 주기에 재시도", orderId, e);
        }
    }
}
