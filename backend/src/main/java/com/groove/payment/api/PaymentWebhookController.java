package com.groove.payment.api;

import com.groove.common.idempotency.IdempotencyService;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.api.dto.PaymentWebhookRequest;
import com.groove.payment.application.PaymentCallbackService;
import com.groove.payment.gateway.WebhookSignatureVerifier;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 결과 웹훅 수신 API (API.md §3.6, #W7-4).
 *
 * <p>PG 가 비동기 결제 결과를 {@code POST /api/v1/payments/webhook} 으로 통보한다. 인증 토큰 대신
 * {@code X-Mock-Signature} 헤더로 서명을 검증한다(Mock 은 공유 시크릿 단순 비교, 실패 시 401 — 실 PG 는
 * PG별 서명 헤더를 쓰는 구현체로 교체, API.md §3.6) — SecurityConfig 에서 permitAll. 멱등성은 클라이언트
 * {@code Idempotency-Key} 가 아니라 본문의 {@code pgTransactionId} 에서 키를 합성해 {@link IdempotencyService}
 * 로 보장한다 (PG 는 우리 멱등 헤더를 보내지 않으므로 {@code @Idempotent} 가 아니다). 컨트롤러는 비트랜잭션 —
 * {@link PaymentCallbackService#applyResult} 가 자기 트랜잭션을 커밋한 뒤 멱등성 마커가 갱신되도록
 * ({@code IdempotencyService} 호출 규약).
 *
 * <p>인프로세스 Mock 웹훅({@code MockWebhookSimulator} → {@code PaymentWebhookHandler})·폴링 스케줄러도 같은
 * 처리 경로·같은 멱등성 키를 공유하므로, 어느 조합으로 중복 수신해도 상태 전이는 1회다(중복은 무해하게 무시).
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentWebhookController {

    static final String SIGNATURE_HEADER = "X-Mock-Signature";

    private final WebhookSignatureVerifier signatureVerifier;
    private final PaymentCallbackService callbackService;
    private final IdempotencyService idempotencyService;

    public PaymentWebhookController(WebhookSignatureVerifier signatureVerifier,
                                    PaymentCallbackService callbackService,
                                    IdempotencyService idempotencyService) {
        this.signatureVerifier = signatureVerifier;
        this.callbackService = callbackService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<PaymentCallbackResult> handle(
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
            @Valid @RequestBody PaymentWebhookRequest body) {
        signatureVerifier.verify(signature);
        PaymentCallbackResult result = idempotencyService.execute(
                PaymentCallbackService.idempotencyKeyFor(body.pgTransactionId()),
                PaymentCallbackResult.class,
                () -> callbackService.applyResult(body.pgTransactionId(), body.status(), body.failureReason()));
        return ResponseEntity.ok(result);
    }
}
