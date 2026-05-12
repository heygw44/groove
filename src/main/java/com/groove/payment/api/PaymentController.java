package com.groove.payment.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.common.idempotency.IdempotencyService;
import com.groove.common.idempotency.web.Idempotent;
import com.groove.common.idempotency.web.IdempotencyKeyInterceptor;
import com.groove.payment.api.dto.PaymentApiResponse;
import com.groove.payment.api.dto.PaymentCreateRequest;
import com.groove.payment.application.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 API (API.md §3.6).
 *
 * <p>{@code POST /payments} 는 {@code @Idempotent} — {@link IdempotencyKeyInterceptor} 가
 * {@code Idempotency-Key} 헤더를 검증(없으면 400)하고, 처리 본체는 {@link IdempotencyService#execute}
 * 를 통해 같은 키당 한 번만 실행된다. 같은 키 재요청은 캐시된 응답을 그대로 받는다(replay 시에도 202).
 * 컨트롤러는 비트랜잭션이어야 한다 — {@code PaymentService.requestPayment} 가 자기 트랜잭션을 커밋한 뒤
 * 멱등성 마커가 COMPLETED 로 갱신되도록 ({@code IdempotencyService} 호출 규약).
 *
 * <p>회원/게스트 분기: {@code @AuthenticationPrincipal(required = false)} 로 토큰 유무를 받는다.
 * {@code POST /payments} 는 SecurityConfig 에서 permitAll — 회원 주문 결제는 서비스 레이어가 본인
 * 여부를 검증하고, 게스트 주문은 익명 호출자도 결제를 시작할 수 있다. {@code GET /payments/{id}} 는
 * {@code anyRequest().authenticated()} 기본 정책으로 보호되는 회원 전용 엔드포인트다.
 *
 * <p>TODO(W10): {@code POST /payments} 회원당 분당 5회 레이트 리밋 (API.md §3.6) — 본 이슈(#W7-3) 범위 외.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    public PaymentController(PaymentService paymentService, IdempotencyService idempotencyService) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    @Idempotent
    public ResponseEntity<PaymentApiResponse> request(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PaymentCreateRequest body,
            HttpServletRequest httpRequest) {
        String idempotencyKey = (String) httpRequest.getAttribute(IdempotencyKeyInterceptor.KEY_ATTRIBUTE);
        Long callerMemberId = principal != null ? principal.memberId() : null;
        String fingerprint = body.orderNumber() + "|" + body.method();

        PaymentApiResponse response = idempotencyService.execute(
                idempotencyKey, fingerprint, PaymentApiResponse.class,
                () -> paymentService.requestPayment(callerMemberId, body));
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentApiResponse> get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.findForMember(principal.memberId(), paymentId));
    }
}
