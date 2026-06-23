package com.groove.payment.application;

import com.groove.common.idempotency.IdempotencyService;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.domain.PaymentStatus;
import org.springframework.stereotype.Service;

/**
 * PG 종착 상태(PAID/FAILED)를 공유 멱등키로 1회 적용하는 정산 헬퍼.
 *
 * <p>웹훅({@link TossWebhookService})·폴링({@link PaymentReconciliationScheduler})·만료 리퍼가 공유하던
 * {@code idempotencyService.execute(idempotencyKeyFor(pgTx), PaymentCallbackResult.class, () -> applyResult(...))}
 * 삼중주를 한곳으로 모은다. 멱등키 합성·결과 타입·콜백 위임이 흩어지지 않도록 단일 진입점으로 유지한다.
 *
 * <p>{@code execute} 는 열린 트랜잭션 밖에서 호출해야 하므로 이 메서드는 비-트랜잭션이며, 별도 빈인
 * {@link PaymentCallbackService#applyResult}(자체 @Transactional)를 프록시 경유로 호출한다(자기호출 우회 금지).
 */
@Service
public class PaymentSettlementService {

    private final IdempotencyService idempotencyService;
    private final PaymentCallbackService callbackService;

    public PaymentSettlementService(IdempotencyService idempotencyService, PaymentCallbackService callbackService) {
        this.idempotencyService = idempotencyService;
        this.callbackService = callbackService;
    }

    /**
     * PG 종착 상태를 공유 멱등키({@code payment-callback:{pgTransactionId}})로 1회 적용한다.
     * {@code terminalStatus} 는 PAID/FAILED 여야 한다(검증은 {@link PaymentCallbackService#applyResult} 가 수행).
     * 중복 호출·동시 콜백은 캐시 재생 또는 비-PENDING 흡수로 무해하다.
     */
    public PaymentCallbackResult settle(String pgTransactionId, PaymentStatus terminalStatus, String failureReason) {
        return idempotencyService.execute(
                PaymentCallbackService.idempotencyKeyFor(pgTransactionId),
                PaymentCallbackResult.class,
                () -> callbackService.applyResult(pgTransactionId, terminalStatus, failureReason));
    }
}
