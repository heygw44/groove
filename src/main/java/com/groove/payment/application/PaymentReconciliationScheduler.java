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
 * 결제 폴링 동기화 스케줄러 (#W7-4 DoD) — 웹훅 유실에 대비해 일정 시간 이상 {@link PaymentStatus#PENDING}
 * 으로 머문 결제를 주기적으로 PG {@code query()} 로 조회해 결과를 동기화한다.
 *
 * <p>대상은 {@code created_at} 이 {@code now - groove.payment.reconciliation.min-age} 이전인 PENDING 결제다 —
 * 갓 접수된 결제는 정상 웹훅이 곧 도착하므로 제외한다. 동기화는 웹훅 경로와 동일한 {@link PaymentCallbackService}
 * 를 동일한 {@code IdempotencyService} 키로 호출하므로, 웹훅과 경쟁해도 상태 전이는 1회다. 한 건의 실패가 배치
 * 전체를 막지 않도록 건별로 격리하고(다음 주기에 재시도), 스케줄러 스레드 밖으로 예외를 흘리지 않는다.
 * 한 주기 처리량은 {@code .batch-size} 로 제한해(메모리 바운드) 적체 시에도 나머지는 다음 주기에 처리한다.
 *
 * <p>실행 주기/초기 지연은 {@code groove.payment.reconciliation.{interval,initial-delay}}, 대상 최소 경과
 * 시간은 {@code .min-age}, 주기당 처리 상한은 {@code .batch-size}. 전역 {@code @EnableScheduling} 은
 * {@code common.scheduling.SchedulingConfig} 에 있다 — 자체 {@code @EnableScheduling} 을 두지 않는다.
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
