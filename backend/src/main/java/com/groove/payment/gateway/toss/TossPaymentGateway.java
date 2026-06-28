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
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
 * 토스페이먼츠 실 PG 어댑터. tossRestClient(Basic Auth)로 코어 API 를 호출한다.
 * dev/prod 에서만 로드한다(그 외는 MockPaymentGateway, 프로파일 택1).
 * 동기 confirm 모델이라 {@link #confirm}이 진입점. {@link #request}에 대응하는 토스 API 가 없어 UnsupportedOperationException.
 * confirm/refund 는 Idempotency-Key 헤더로 재시도 중복을 막는다(confirm=paymentKey, refund=RefundRequest#idempotencyKey()).
 * 502 매핑: confirm/query 는 공통 호출부 래퍼가 없어 어댑터가 직접 PaymentGatewayException(502)으로 래핑한다.
 * refund 는 호출부가 GatewayRefunds 로 래핑하므로 raw RuntimeException 으로 전파한다(더블래핑 방지).
 */
@Component
@Profile({"dev", "prod"})
public class TossPaymentGateway implements PaymentGateway {

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
     * 토스 호출을 서킷브레이커(바깥)·재시도(안쪽)로 감싼다. 서킷 OPEN 이면 호출 전 빠르게 실패해 워커 점유를 끊고,
     * 일시 장애는 재시도가 흡수한다. 서킷은 재시도까지 포함한 호출 단위 결과를 집계한다(재시도로 회복되면 실패로 안 셈).
     */
    private <T> T callGuarded(Supplier<T> call) {
        return CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, call)).get();
    }

    /**
     * 재시도·서킷브레이커가 일시 장애로 간주할 예외 술어. 토스 5xx 와 연결 단계 실패(연결 거부·연결 타임아웃)만 일시 장애다.
     * 4xx·알 수 없는 상태는 재시도/집계하지 않는다. 읽기 타임아웃은 이미 read-timeout 을 소모해 재시도하지 않는다(워커 점유 증폭 방지).
     * CallNotPermittedException 도 비대상이라 서킷 OPEN 시 즉시 전파된다.
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
            // paymentKey 는 confirm 의 자연 멱등 단위다. 재호출에도 토스가 Idempotency-Key 로 dedup 한다.
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
            // 권위 정산금액(totalAmount)을 함께 반환한다. 웹훅/폴링이 PAID 정산 전 위변조 대조에 쓴다.
            PaymentStatus status = TossStatusMapper.toPaymentStatus(requireBody(payment).status());
            return new GatewayQuery(status, payment.totalAmount());
        } catch (CallNotPermittedException circuitOpen) {
            // 서킷 OPEN 은 일시 백오프다. 502(영구 오류)로 정규화하지 않고 그대로 전파해, 폴링 스케줄러가 이를
            // '영구 해소 불가'로 오인해 만료 PENDING 결제를 FAILED 로 종결하지 않게 한다.
            throw circuitOpen;
        } catch (RuntimeException e) {
            // query 도 호출부가 502 로 변환하지 않으므로 어댑터가 직접 정규화한다.
            logTossError("결제 조회", e);
            throw new PaymentGatewayException(e);
        }
    }

    @Override
    public RefundResponse refund(RefundRequest request) {
        // 토스 cancelReason 은 필수다. 포트 계약상 reason 은 선택이므로 비어 있으면 기본 사유로 채운다.
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
            // GatewayRefunds 가 502 로 정규화하므로 raw 로 재던진다(더블래핑 방지). 진단만 남긴다.
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
