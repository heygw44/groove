package com.groove.payment.gateway.toss;

import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentGatewayException;
import com.groove.payment.gateway.ConfirmResponse;
import com.groove.payment.gateway.GatewayQuery;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.PaymentRequest;
import com.groove.payment.gateway.PaymentResponse;
import com.groove.payment.gateway.RefundRequest;
import com.groove.payment.gateway.RefundResponse;
import com.groove.payment.gateway.toss.dto.TossCancelRequest;
import com.groove.payment.gateway.toss.dto.TossConfirmRequest;
import com.groove.payment.gateway.toss.dto.TossErrorResponse;
import com.groove.payment.gateway.toss.dto.TossPayment;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 토스페이먼츠 실 PG 어댑터(#294). #293 의 {@code tossRestClient}(Basic Auth) 를 주입해 코어 API 를 호출한다.
 * dev/prod 프로파일에서만 로드되며, test/local/docker 는 {@code MockPaymentGateway} 를 쓴다(프로파일로 택1).
 *
 * <p><b>승인 모델:</b> 토스는 동기 confirm 모델이라 {@link #confirm}이 진입점이다. 비동기 {@link #request}
 * (request→PENDING→웹훅)에 대응하는 토스 API 가 없어 {@code UnsupportedOperationException} 을 던진다.
 * confirm 을 호출하는 컨트롤러/프론트 결제위젯 배선은 M17 후속 이슈다.
 *
 * <p><b>멱등성:</b> {@link #confirm}/{@link #refund} 는 토스 POST API 라 {@code Idempotency-Key} 헤더로
 * 재시도 중복을 막는다. confirm 은 자연 멱등 단위인 {@code paymentKey} 를, refund 는 결정적으로 조립한
 * {@code RefundRequest#idempotencyKey()} 를 키로 쓴다(둘 다 같은 시도 → 같은 키 → 토스 dedup).
 *
 * <p><b>502 매핑 정책:</b> {@code tossRestClient} 는 커스텀 status handler 가 없어 4xx/5xx 를
 * {@code RestClientResponseException}(RuntimeException)으로 전파한다.
 * <ul>
 *   <li>{@link #confirm}/{@link #query} — 공통 호출부 래퍼가 없으므로 어댑터가 직접 PaymentGatewayException(502)으로 래핑.</li>
 *   <li>{@link #refund} — 두 호출부(AdminOrderService/ClaimService)가 {@code GatewayRefunds} 로 래핑하므로
 *       어댑터는 래핑하지 않고 raw RuntimeException 을 전파한다(더블래핑 방지).</li>
 * </ul>
 * 세 메서드 모두 실패 시 {@code logTossError} 로 토스 에러({code, message})를 진단 로깅한다.
 */
@Component
@Profile({"dev", "prod"})
public class TossPaymentGateway implements PaymentGateway {

    /** provider 식별자. */
    public static final String PROVIDER = "TOSS";

    /** 환불 사유 미지정 시 토스 cancelReason(필수)에 채울 기본값. */
    static final String DEFAULT_CANCEL_REASON = "가맹점 환불 처리";

    private static final Logger log = LoggerFactory.getLogger(TossPaymentGateway.class);

    private final RestClient tossRestClient;
    private final Clock clock;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public TossPaymentGateway(@Qualifier("tossRestClient") RestClient tossRestClient, Clock clock,
                              CircuitBreaker circuitBreaker, Retry retry) {
        this.tossRestClient = Objects.requireNonNull(tossRestClient, "tossRestClient");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.retry = Objects.requireNonNull(retry, "retry");
    }

    /**
     * 토스 호출을 서킷브레이커(바깥)·재시도(안쪽)로 감싼다(#320). 서킷이 OPEN 이면 호출 전 빠르게 실패
     * (CallNotPermittedException)해 워커 점유를 끊고, 일시 장애는 재시도가 흡수한다. 서킷은 재시도까지 포함한
     * 호출 단위 결과를 집계한다(재시도로 회복되면 실패로 세지 않음).
     */
    private <T> T callGuarded(Supplier<T> call) {
        return CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, call)).get();
    }

    /**
     * 재시도·서킷브레이커가 일시 장애로 간주할 예외 술어(#320). 토스 5xx 와 연결 단계 실패(연결 거부·연결 타임아웃)만
     * 일시 장애다. 4xx(결정적 클라이언트 오류)·알 수 없는 상태(IllegalStateException)는 재시도/집계하지 않는다.
     * 읽기 타임아웃({@link java.net.http.HttpTimeoutException} 중 connect 가 아닌 것)은 이미 read-timeout 을 소모했으므로
     * 재시도하지 않는다 — 동기 confirm 워커 점유 증폭 방지. {@code CallNotPermittedException} 도 비대상이라 서킷 OPEN 시 즉시 전파된다.
     */
    static boolean isRetryableTransient(Throwable throwable) {
        if (throwable instanceof RestClientResponseException response) {
            return response.getStatusCode().is5xxServerError();
        }
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PaymentResponse request(PaymentRequest request) {
        throw new UnsupportedOperationException(
                "토스페이먼츠는 confirm 승인 모델입니다 — request() 대신 confirm(paymentKey, orderId, amount) 를 사용하세요");
    }

    @Override
    public ConfirmResponse confirm(String paymentKey, String orderId, long amount) {
        try {
            // paymentKey 는 confirm 의 자연 멱등 단위다 — 재시도·read-timeout 후 재호출에도 토스가 Idempotency-Key 로 dedup 한다.
            TossPayment payment = callGuarded(() -> tossRestClient.post()
                    .uri("/v1/payments/confirm")
                    .header("Idempotency-Key", paymentKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossConfirmRequest(paymentKey, orderId, amount))
                    .retrieve()
                    .body(TossPayment.class));
            PaymentStatus status = TossStatusMapper.toPaymentStatus(requireBody(payment).status());
            PaymentMethod method = TossMethodMapper.toPaymentMethod(payment.method());
            log.info("토스 결제 승인: paymentKey={}, orderId={}, amount={} → {} ({})",
                    paymentKey, orderId, amount, status, method);
            return new ConfirmResponse(payment.paymentKey(), status, method);
        } catch (RuntimeException e) {
            // confirm 은 호출부 공통 래퍼가 없으므로 어댑터가 직접 502 로 정규화한다.
            logTossError("결제 승인", e);
            throw new PaymentGatewayException(e);
        }
    }

    @Override
    public GatewayQuery query(String pgTransactionId) {
        try {
            TossPayment payment = callGuarded(() -> tossRestClient.get()
                    .uri("/v1/payments/{paymentKey}", pgTransactionId)
                    .retrieve()
                    .body(TossPayment.class));
            // 토스가 알려준 권위 정산금액(totalAmount)을 함께 반환 — 웹훅/폴링이 PAID 정산 전 위변조 대조에 쓴다(#320).
            PaymentStatus status = TossStatusMapper.toPaymentStatus(requireBody(payment).status());
            return new GatewayQuery(status, payment.totalAmount());
        } catch (RuntimeException e) {
            // query 도 호출부(폴링 스케줄러)가 502 로 변환하지 않으므로 어댑터가 직접 정규화한다.
            logTossError("결제 조회", e);
            throw new PaymentGatewayException(e);
        }
    }

    @Override
    public RefundResponse refund(RefundRequest request) {
        // 토스 cancelReason 은 필수다 — 포트 계약상 reason 은 선택이므로 비어 있으면 기본 사유로 채운다.
        String cancelReason = (request.reason() == null || request.reason().isBlank())
                ? DEFAULT_CANCEL_REASON : request.reason();
        try {
            TossPayment payment = callGuarded(() -> tossRestClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", request.pgTransactionId())
                    .header("Idempotency-Key", request.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossCancelRequest(cancelReason, request.amount()))
                    .retrieve()
                    .body(TossPayment.class));
            PaymentStatus status = TossStatusMapper.toPaymentStatus(requireBody(payment).status());
            log.info("토스 결제 취소: paymentKey={}, amount={} → {}", request.pgTransactionId(), request.amount(), status);
            return new RefundResponse(payment.paymentKey(), status, clock.instant());
        } catch (RuntimeException e) {
            // GatewayRefunds 가 PaymentGatewayException(502)으로 정규화하므로 raw 로 재던진다(더블래핑 방지). 진단만 남긴다.
            logTossError("결제 취소", e);
            throw e;
        }
    }

    private static TossPayment requireBody(TossPayment payment) {
        if (payment == null) {
            throw new IllegalStateException("토스 응답 본문이 비어 있습니다");
        }
        return payment;
    }

    /** 토스 호출 실패의 에러 응답({code, message})을 진단 로깅한다(confirm/query/refund 공통). */
    private static void logTossError(String operation, RuntimeException e) {
        if (e instanceof RestClientResponseException response) {
            TossErrorResponse error = parseError(response);
            log.warn("토스 {} 실패: httpStatus={}, code={}, message={}",
                    operation, response.getStatusCode(), error.code(), error.message());
        } else {
            log.warn("토스 {} 호출 오류", operation, e);
        }
    }

    /** 에러 바디를 TossErrorResponse 로 파싱하되, 파싱 불가 시 빈 값으로 폴백한다(로깅 전용). */
    private static TossErrorResponse parseError(RestClientResponseException response) {
        try {
            TossErrorResponse error = response.getResponseBodyAs(TossErrorResponse.class);
            return error != null ? error : new TossErrorResponse(null, null);
        } catch (RuntimeException ignored) {
            return new TossErrorResponse(null, null);
        }
    }
}
