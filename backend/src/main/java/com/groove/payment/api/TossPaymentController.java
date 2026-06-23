package com.groove.payment.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.common.idempotency.IdempotencyService;
import com.groove.common.idempotency.web.Idempotent;
import com.groove.common.idempotency.web.IdempotencyKeyInterceptor;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.api.dto.PaymentCreateRequest;
import com.groove.payment.api.dto.TossCheckoutResponse;
import com.groove.payment.application.TossPaymentService;
import com.groove.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 토스페이먼츠 동기 confirm 승인 흐름 API(#295).
 *
 * <p>POST /api/v1/payments/toss/checkout — 결제 요청(회원/게스트, Idempotency-Key 필수). 프론트 결제위젯 초기화 값 응답.
 * GET /payments/toss/success·/fail — 토스가 브라우저를 리다이렉트하는 타깃. 서버 confirm/보상 처리 후 SPA 결과 라우트로 302.
 *
 * <p><b>보안 주의(브라우저 리다이렉트 콜백):</b> success/fail 은 토스가 브라우저를 보내는 미인증 GET 타깃이라 Bearer 인증이 없다.
 * 보상(fail)·승인(success)은 PENDING 결제에 대해 멱등으로만 동작하지만, orderNumber 만 알면 누구나 호출할 수 있어
 * 교차 주문 조작(타인의 진행 중 결제 강제 실패 등) 여지가 있다. 결제마다 발급한 토큰을 successUrl/failUrl 쿼리에 실어
 * 검증하는 강한 보호는 per-payment successUrl 을 지정하는 프론트 결제위젯(M17 후속 이슈)에서 완성한다.
 * 또한 어떤 예외가 나도 JSON 을 노출하지 않고 항상 SPA 결과 라우트로 302 리다이렉트한다.
 */
@Tag(name = "결제(토스)", description = "토스페이먼츠 confirm 승인 흐름 — checkout · successUrl/failUrl 콜백")
@RestController
public class TossPaymentController {

    private static final Logger log = LoggerFactory.getLogger(TossPaymentController.class);

    private final TossPaymentService tossPaymentService;
    private final IdempotencyService idempotencyService;

    public TossPaymentController(TossPaymentService tossPaymentService, IdempotencyService idempotencyService) {
        this.tossPaymentService = tossPaymentService;
        this.idempotencyService = idempotencyService;
    }

    @Operation(summary = "토스 결제 요청(checkout)",
            description = "주문을 검증하고 결제 예정액(payable)을 PENDING 으로 서버 저장한 뒤, 결제위젯 초기화 값(clientKey·orderId·amount)을 응답한다. "
                    + "회원/게스트 공통이며 Idempotency-Key 헤더가 필수다(없으면 400). 동일 키 재요청은 캐시된 응답을 replay 한다. (공개 엔드포인트)")
    @ApiResponse(responseCode = "200", description = "결제 요청 접수 — 위젯 초기화 값 응답")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 또는 Idempotency-Key 헤더 누락")
    @ApiResponse(responseCode = "404", description = "결제 대상 주문 없음 (ORDER_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "멱등 충돌 또는 현재 주문 상태에서 결제 불가")
    @Parameter(in = ParameterIn.HEADER, name = "Idempotency-Key", required = true,
            description = "멱등 키 — 같은 키 재요청은 캐시된 응답을 replay 한다",
            example = "9f1c2e6a-3b4d-4f5a-8c7e-1a2b3c4d5e6f")
    @PostMapping("/api/v1/payments/toss/checkout")
    @Idempotent
    public ResponseEntity<TossCheckoutResponse> checkout(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PaymentCreateRequest body,
            HttpServletRequest httpRequest) {
        String idempotencyKey = (String) httpRequest.getAttribute(IdempotencyKeyInterceptor.KEY_ATTRIBUTE);
        Long callerMemberId = principal != null ? principal.memberId() : null;
        String fingerprint = body.orderNumber() + "|" + body.method();

        TossCheckoutResponse response = idempotencyService.execute(
                idempotencyKey, fingerprint, TossCheckoutResponse.class,
                () -> tossPaymentService.checkout(callerMemberId, body));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "토스 successUrl 콜백(confirm)",
            description = "토스 인증 완료 후 paymentKey·orderId·amount 로 브라우저가 리다이렉트되는 타깃. 저장 payable 과 amount 일치를 검증하고(위변조 차단) "
                    + "게이트웨이 confirm 으로 승인한 뒤 order PAID + 배송 이벤트를 발행한다. successUrl 새로고침/재호출에도 1회만 전이한다. "
                    + "어떤 오류(위변조·만료·미확정·잘못된 주문)에도 JSON 을 노출하지 않고 SPA 결과 라우트로 302 한다.")
    @ApiResponse(responseCode = "302", description = "처리 후 /orders/{orderNumber}?payment=success|fail 로 리다이렉트")
    @GetMapping("/payments/toss/success")
    public ResponseEntity<Void> success(
            @RequestParam(required = false) String paymentKey,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String token) {
        String status;
        try {
            // 미인증 공개 콜백 — 과대 입력이 서비스/게이트웨이/로그에 닿기 전에 컨트롤러에서 경계 검증한다(위반 시 아래 catch 가 fail 302).
            // @Validated 대신 in-method 가드를 쓰는 이유: 빈 검증 위반은 메서드 실행 전에 던져져 GlobalExceptionHandler 가 JSON 400 을
            // 응답 → "어떤 예외도 JSON 누출 없이 항상 302 fail" 불변식을 깨뜨린다. 가드는 catch 안으로 흘러 불변식을 보존한다.
            requireBoundedParams(paymentKey, orderId, amount, token);
            // token 은 checkout 이 successUrl 에 박은 결제별 토큰 — confirm 이 저장 토큰과 일치를 검증해 교차 주문 조작을 차단한다(#304).
            PaymentCallbackResult result = tossPaymentService.confirm(paymentKey, orderId, Long.parseLong(amount), token);
            status = result.paymentStatus() == PaymentStatus.PAID ? "success" : "fail";
        } catch (RuntimeException e) {
            // successUrl 은 브라우저 리다이렉트 타깃 — 위변조·토큰 불일치·만료·미확정·파라미터 오류·경계 초과 등 어떤 예외도 JSON 누출 없이 fail 안내한다.
            log.warn("토스 confirm 처리 실패 — fail 리다이렉트: orderId={}, err={}", orderId, e.toString());
            status = "fail";
        }
        return redirect(orderId, status);
    }

    @Operation(summary = "토스 failUrl 콜백",
            description = "결제 실패/취소 시 code·message·orderId 로 브라우저가 리다이렉트되는 타깃. 미인증 공개 GET 이라 서버 상태는 바꾸지 않고 "
                    + "SPA 결과 라우트로 안내(302)만 한다. 실제 보상(재고·쿠폰 복원)은 미확정 PENDING 결제를 만료 처리하는 폴링 리퍼가 담당한다.")
    @ApiResponse(responseCode = "302", description = "/orders/{orderNumber}?payment=fail 로 리다이렉트(상태 변경 없음)")
    @GetMapping("/payments/toss/fail")
    public ResponseEntity<Void> fail(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String token) {
        // 보안(#295 리뷰): failUrl 은 토스가 보내는 미인증 브라우저 GET 이라 orderNumber 만으로 호출 가능 — 여기서 결제를
        // FAILED 로 바꾸면 제3자가 타인의 진행 중 결제를 강제 실패시킬 수 있다(CSRF). 따라서 안내 리다이렉트만 하고,
        // 보상은 폴링 리퍼(PaymentReconciliationScheduler)의 신뢰 가능한 만료 경로가 1회 수행한다.
        // token 은 #304 로 round-trip 되지만 fail 은 상태를 바꾸지 않으므로 강제 검증 대신 안내만 한다(success 게이트가 핵심).
        // 토큰 값은 로그에 남기지 않고 존재 여부만 기록한다(추적용).
        log.info("토스 결제 실패/취소 안내: orderId={}, code={}, token={}",
                orderId, code, token != null ? "present" : "absent");
        return redirect(orderId, "fail");
    }

    /** 미인증 공개 콜백 파라미터(orderId·amount·token·paymentKey)의 최대 허용 길이. orderNumber 는 ~20자, 토큰은 UUID(36자). */
    private static final int MAX_CALLBACK_PARAM_LENGTH = 64;

    /** 콜백 파라미터 길이 경계 — 하나라도 초과하면 예외(success 의 catch 가 fail 302 로 흡수). */
    private static void requireBoundedParams(String... params) {
        for (String p : params) {
            if (p != null && p.length() > MAX_CALLBACK_PARAM_LENGTH) {
                throw new IllegalArgumentException("토스 콜백 파라미터 길이 초과");
            }
        }
    }

    /** /orders/{orderNumber}?payment={status} 상대경로(동일 오리진)로 302. orderNumber 가 없거나 경계 초과면 /orders 로 폴백한다. */
    private static ResponseEntity<Void> redirect(String orderNumber, String status) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/orders");
        if (orderNumber != null && !orderNumber.isBlank() && orderNumber.length() <= MAX_CALLBACK_PARAM_LENGTH) {
            builder.pathSegment(orderNumber); // 단일 세그먼트로 인코딩 — 경로/헤더 인젝션·과대 입력 방지.
        }
        URI location = builder.queryParam("payment", status).encode().build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
