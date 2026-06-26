package com.groove.shipping.application;

import com.groove.shipping.domain.Shipping;
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
import java.util.function.Consumer;

/**
 * 배송 자동 진행 스케줄러 — 배송 상태를 PREPARING → SHIPPED → DELIVERED 로 한 단계씩 자동 진행시킨다.
 *
 * 틱마다 한 단계만 민다 — PREPARING 으로 prepare-delay 이상 머문 배송을 SHIPPED 로, SHIPPED 로 ship-delay 이상
 * 머문 배송을 DELIVERED 로. 상태 전이는 식별자 단위로 ShippingService 의 트랜잭션 메서드에 위임하고, 건별
 * try/catch 로 격리한다(다음 주기 재시도). 한 주기 처리량은 batch-size 로 제한한다.
 */
@Component
public class ShippingProgressScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShippingProgressScheduler.class);

    private final ShippingRepository shippingRepository;
    private final ShippingService shippingService;
    private final Clock clock;
    private final Duration prepareDelay;
    private final Duration shipDelay;
    private final Limit batchLimit;

    public ShippingProgressScheduler(ShippingRepository shippingRepository,
                                     ShippingService shippingService,
                                     Clock clock,
                                     @Value("${groove.shipping.progress.prepare-delay:PT5S}") Duration prepareDelay,
                                     @Value("${groove.shipping.progress.ship-delay:PT5S}") Duration shipDelay,
                                     @Value("${groove.shipping.progress.batch-size:200}") int batchSize) {
        this.shippingRepository = shippingRepository;
        this.shippingService = shippingService;
        this.clock = clock;
        this.prepareDelay = Objects.requireNonNull(prepareDelay, "prepareDelay");
        this.shipDelay = Objects.requireNonNull(shipDelay, "shipDelay");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("groove.shipping.progress.batch-size 는 양수여야 합니다: " + batchSize);
        }
        this.batchLimit = Limit.of(batchSize);
    }

    @Scheduled(
            fixedDelayString = "${groove.shipping.progress.interval:PT2S}",
            initialDelayString = "${groove.shipping.progress.initial-delay:PT5S}")
    public void progressShipments() {
        Instant now = clock.instant();
        advance("PREPARING→SHIPPED",
                shippingRepository.findByStatusAndCreatedAtBefore(ShippingStatus.PREPARING, now.minus(prepareDelay), batchLimit),
                shipping -> shippingService.advanceToShipped(shipping.getId()));
        advance("SHIPPED→DELIVERED",
                shippingRepository.findByStatusAndShippedAtBefore(ShippingStatus.SHIPPED, now.minus(shipDelay), batchLimit),
                shipping -> shippingService.advanceToDelivered(shipping.getId()));
    }

    private void advance(String step, List<Shipping> candidates, Consumer<Shipping> transition) {
        if (candidates.isEmpty()) {
            return;
        }
        log.debug("배송 자동 진행 {} 대상 {}건 (limit={})", step, candidates.size(), batchLimit.max());
        for (Shipping shipping : candidates) {
            try {
                transition.accept(shipping);
            } catch (RuntimeException e) {
                log.warn("배송 자동 진행 실패: {} shippingId={} — 다음 주기에 재시도", step, shipping.getId(), e);
            }
        }
    }
}
