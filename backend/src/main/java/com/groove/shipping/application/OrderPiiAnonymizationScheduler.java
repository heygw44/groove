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
 * 주문/배송 PII 익명화 배치 스케줄러 (#170 Part B, GDPR/개인정보 파기·익명화 의무) — 배송완료({@link ShippingStatus#DELIVERED})
 * 된 지 보존기간({@code groove.privacy.order-anonymization.retention})이 지난 주문/배송의 수령인·주소·게스트 PII 를
 * 주기적으로 마스킹한다. 회원/게스트 주문 모두 대상이며, 특히 게스트 주문 PII 는 회원 탈퇴 경로가 없어 이 배치가
 * 유일한 익명화 수단이다.
 *
 * <p>탈퇴 즉시 익명화되는 회원 본인 PII({@code MemberService.withdraw})와 달리, 주문/배송 배송지 PII 는 배송·환불
 * 분쟁 대응을 위해 보존기간 동안 유지한 뒤 익명화한다. 대상은 {@code delivered_at} 이
 * {@code now - retention} 이전인 DELIVERED 배송 중 아직 익명화되지 않은({@code anonymized_at IS NULL}) 건이다.
 *
 * <p>{@link ShippingReconciliationScheduler} 와 동일한 패턴을 따른다 — 경량 projection 으로 대상 id 를 조회하고,
 * 건별로 {@link OrderPiiAnonymizer}(REQUIRES_NEW)를 호출해 격리한다. 한 건의 실패가 배치 전체를 막지 않도록
 * try/catch 로 격리하고(다음 주기 재시도), 스케줄러 스레드 밖으로 예외를 흘리지 않는다. 한 주기 처리량은
 * {@code .batch-size} 로 제한한다(메모리 바운드). 전역 {@code @EnableScheduling} 은
 * {@code common.scheduling.SchedulingConfig} 에 있다 — 자체 {@code @EnableScheduling} 을 두지 않는다.
 *
 * <p>배송이 생성되지 않는 종착 주문(미결제 PENDING / PAYMENT_FAILED / 배송 생성 전 CANCELLED)의 게스트·배송지
 * PII 는 배송완료 기준 배치가 닿지 않으므로, {@link #anonymizeTerminalNonShippingOrders()} 가 주문 상태 +
 * {@code updated_at}(종착 시각) + 보존기간 기준으로 별도 익명화한다(#188). 환불로 취소돼 배송 행을 가진 주문은
 * {@link OrderPiiAnonymizer#anonymizeOrder} 가 배송 PII 까지 함께 마스킹한다.
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
            // 보존기간이 0/음수면 cutoff 가 미래가 돼 배송완료 직후(보존기간 전)에 PII 가 익명화될 수 있다.
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
     * 배송이 없는 종착 주문(#188) PII 익명화 — {@link #anonymizeDeliveredOrders()} 와 동일한 패턴이되 대상이
     * 주문 상태({@link OrderPiiAnonymizer#TERMINAL_NON_SHIPPING_STATUSES}) + {@code updated_at} 기준이다.
     * {@code anonymized_at IS NULL} 필터로 멱등하며, 건별 try/catch 로 한 건의 실패가 배치를 막지 않는다(다음 주기 재시도).
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
