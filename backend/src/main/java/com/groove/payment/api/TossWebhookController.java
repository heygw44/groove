package com.groove.payment.api;

import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.api.dto.TossWebhookRequest;
import com.groove.payment.application.TossWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스페이먼츠 웹훅 수신 API(#296).
 *
 * <p>토스가 결제 상태 변경을 {@code POST /api/v1/payments/toss/webhook} 으로 통보한다. 결제 웹훅
 * ({@code PAYMENT_STATUS_CHANGED})은 서명 헤더가 없어, 본문을 신뢰하는 대신 {@code paymentKey} 로 결제 조회 API 를
 * 재호출(재조회 검증)해 권위 상태만 적용한다(위조 본문 무력화). permitAll 공개 엔드포인트.
 *
 * <p>토스는 10초 내 2xx 미응답 시 최대 7회 재전송하므로, 가벼운 재조회+적용을 동기로 처리하고 빠르게 200 으로 ACK 한다.
 * 멱등키 {@code payment-callback:{paymentKey}} 를 confirm·폴링과 공유해 중복 수신·이중 처리에도 상태 전이는 1회다.
 */
@Tag(name = "토스 결제 웹훅", description = "토스페이먼츠 결제 상태 변경 웹훅 수신 (서명 대신 결제 조회 재검증)")
@RestController
@RequestMapping("/api/v1/payments")
public class TossWebhookController {

    private final TossWebhookService tossWebhookService;

    public TossWebhookController(TossWebhookService tossWebhookService) {
        this.tossWebhookService = tossWebhookService;
    }

    @Operation(summary = "토스 결제 웹훅 수신",
            description = "토스가 결제 상태 변경(PAYMENT_STATUS_CHANGED)을 통보하는 콜백. 로컬 PENDING 결제에 한해 paymentKey 로 "
                    + "결제를 재조회해 권위 상태(PAID/FAILED)만 적용하므로 위조 본문으로는 상태를 바꿀 수 없다. 미지/종착/대상 외/누락은 모두 "
                    + "무해 무시(200)하며, 멱등키로 중복 수신·동시 처리도 무해하다. (공개 엔드포인트)")
    @ApiResponse(responseCode = "200", description = "수신 처리됨 (APPLIED / ALREADY_PROCESSED / IGNORED)")
    @ApiResponse(responseCode = "502", description = "토스 결제 조회 일시 실패 — 재전송 유도 (PAYMENT_GATEWAY_FAILURE)")
    @PostMapping("/toss/webhook")
    public ResponseEntity<PaymentCallbackResult> handle(@RequestBody(required = false) TossWebhookRequest body) {
        // 본문 전체 누락도 무해 무시(200)로 처리하기 위해 required=false — null 은 서비스가 IGNORED 로 흡수한다.
        return ResponseEntity.ok(tossWebhookService.handle(body));
    }
}
