package com.groove.payment.application;

import com.groove.common.idempotency.IdempotencyService;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.domain.PaymentStatus;
import org.springframework.stereotype.Service;

/**
 * PG 종착 상태(PAID/FAILED)를 공유 멱등키로 1회 적용하는 정산 헬퍼. 웹훅·폴링·만료 리퍼가 공유한다.
 * execute 는 열린 트랜잭션 밖에서 호출해야 하므로 비-트랜잭션이며, 별도 빈 {@link PaymentCallbackService#applyResult}
 * 을 프록시 경유로 호출한다(자기호출 우회 금지).
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
     * PG 종착 상태를 공유 멱등키로 1회 적용한다. terminalStatus 는 PAID/FAILED 여야 한다(검증은 applyResult).
     * 중복 호출·동시 콜백은 캐시 재생 또는 비-PENDING 흡수로 무해하다.
     */
    public PaymentCallbackResult settle(String pgTransactionId, PaymentStatus terminalStatus, String failureReason) {
        return idempotencyService.execute(
                PaymentCallbackService.idempotencyKeyFor(pgTransactionId),
                PaymentCallbackResult.class,
                () -> callbackService.applyResult(pgTransactionId, terminalStatus, failureReason));
    }
}
