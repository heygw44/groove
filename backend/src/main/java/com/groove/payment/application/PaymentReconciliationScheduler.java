package com.groove.payment.application;

import com.groove.common.idempotency.IdempotencyService;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.gateway.PaymentGateway;
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
 * 결제 폴링 동기화 스케줄러 — min-age 이상 PENDING 으로 머문 결제를 PG query() 로 조회해 결과를 동기화한다.
 * 동기화는 PaymentCallbackService 를 동일 IdempotencyService 키로 호출한다. 건별로 격리하고 batch-size 로 처리량을 제한한다.
 * 주기/초기지연/최소경과시간/처리상한은 groove.payment.reconciliation.{interval,initial-delay,min-age,batch-size}.
 */
@Component
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentCallbackService callbackService;
    private final IdempotencyService idempotencyService;
    private final Clock clock;
    private final Duration minAge;
    private final Limit batchLimit;

    public PaymentReconciliationScheduler(PaymentRepository paymentRepository,
                                          PaymentGateway paymentGateway,
                                          PaymentCallbackService callbackService,
                                          IdempotencyService idempotencyService,
                                          Clock clock,
                                          @Value("${groove.payment.reconciliation.min-age:PT1M}") Duration minAge,
                                          @Value("${groove.payment.reconciliation.batch-size:200}") int batchSize) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.callbackService = callbackService;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
        this.minAge = Objects.requireNonNull(minAge, "minAge");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("groove.payment.reconciliation.batch-size 는 양수여야 합니다: " + batchSize);
        }
        this.batchLimit = Limit.of(batchSize);
    }

    @Scheduled(
            fixedDelayString = "${groove.payment.reconciliation.interval:PT1M}",
            initialDelayString = "${groove.payment.reconciliation.initial-delay:PT1M}")
    public void reconcilePendingPayments() {
        Instant cutoff = clock.instant().minus(minAge);
        List<Payment> stale = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, cutoff, batchLimit);
        if (stale.isEmpty()) {
            return;
        }
        log.debug("결제 폴링 동기화 대상 {}건 (cutoff={}, limit={})", stale.size(), cutoff, batchLimit.max());
        for (Payment payment : stale) {
            reconcileOne(payment.getPgTransactionId());
        }
    }

    private void reconcileOne(String pgTransactionId) {
        try {
            PaymentStatus pgStatus = paymentGateway.query(pgTransactionId);
            if (pgStatus == PaymentStatus.PENDING) {
                return;
            }
            if (pgStatus != PaymentStatus.PAID && pgStatus != PaymentStatus.FAILED) {
                log.warn("결제 폴링: 예상치 못한 PG 상태 pgTx={}, status={} — 건너뜀", pgTransactionId, pgStatus);
                return;
            }
            PaymentCallbackResult result = idempotencyService.execute(
                    PaymentCallbackService.idempotencyKeyFor(pgTransactionId),
                    PaymentCallbackResult.class,
                    () -> callbackService.applyResult(pgTransactionId, pgStatus, null));
            log.info("결제 폴링 동기화: pgTx={} → {} ({})", pgTransactionId, pgStatus, result.outcome());
        } catch (RuntimeException e) {
            log.warn("결제 폴링 동기화 실패: pgTx={} — 다음 주기에 재시도", pgTransactionId, e);
        }
    }
}
