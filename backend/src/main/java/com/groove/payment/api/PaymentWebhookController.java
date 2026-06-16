package com.groove.payment.api;

import com.groove.common.idempotency.IdempotencyService;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.api.dto.PaymentWebhookRequest;
import com.groove.payment.application.PaymentCallbackService;
import com.groove.payment.gateway.WebhookSignatureVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 결과 웹훅 수신 API.
 *
 * <p>PG 가 비동기 결제 결과를 POST /api/v1/payments/webhook 으로 통보한다. X-Mock-Signature 헤더로 서명을 검증하며(실패 시 401) permitAll 이다.
 * 멱등성은 본문의 pgTransactionId 에서 키를 합성해 보장한다. 같은 키를 공유하는 Mock 웹훅·폴링 스케줄러로 중복 수신해도 상태 전이는 1회다.
 */
@Tag(name = "결제 웹훅", description = "PG 결제 결과 콜백 수신 (인증 토큰이 아니라 X-Mock-Signature 헤더 서명으로 검증)")
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

    @Operation(summary = "결제 결과 웹훅 수신",
            description = "PG 가 비동기 결제 결과(PAID/FAILED)를 통보하는 콜백 엔드포인트. 인증 토큰이 아니라 X-Mock-Signature 헤더 서명으로 검증하며, "
                    + "서명 검증 실패 시 401 로 거부한다. 멱등성은 본문의 pgTransactionId 로 보장되어 중복 콜백은 무해하게 무시된다. (공개 엔드포인트)")
    @ApiResponse(responseCode = "200", description = "콜백 처리됨 (APPLIED / ALREADY_PROCESSED / IGNORED)")
    @ApiResponse(responseCode = "400", description = "본문 검증 실패 (pgTransactionId 누락·status 가 PAID/FAILED 외)")
    @ApiResponse(responseCode = "401", description = "웹훅 서명 검증 실패 (PAYMENT_WEBHOOK_INVALID_SIGNATURE)")
    @PostMapping("/webhook")
    public ResponseEntity<PaymentCallbackResult> handle(
            @Parameter(description = "PG 콜백 서명 헤더 (Mock 은 공유 시크릿 단순 비교)", example = "mock-signature")
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
