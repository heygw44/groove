package com.groove.payment.gateway.toss;

import com.groove.payment.gateway.TossPaymentProperties;
import com.groove.payment.gateway.TossResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 토스페이먼츠 연동 구성 — 코드베이스 최초의 외부 HTTP 클라이언트. dev/prod 에서만 활성(test/local/docker 는 MockPaymentGateway).
 *
 * 커넥션 풀: {@link JdkClientHttpRequestFactory}({@link HttpClient}). JDK21 HttpClient 는 keep-alive 를 풀링·재사용하므로
 * 매 호출 소켓을 새로 여는 SimpleClientHttpRequestFactory 와 달리 토스 지연 시 톰캣 워커가 소켓 생성에 묶이지 않는다.
 * connect 타임아웃은 HttpClient, read 타임아웃은 RequestFactory 에 건다. (세밀 제어 필요 시 Apache HttpClient5 가 업그레이드 경로.)
 * 재시도·서킷브레이커: Resilience4j {@link Retry}·{@link CircuitBreaker} 를 {@link TossPaymentGateway} 에 주입해 confirm/query/refund 를 감싼다(설정 {@link TossResilienceProperties}).
 * 4xx/5xx → 502 매핑: 커스텀 status handler 를 두지 않는다 — 토스 오류는 RestClientResponseException 으로 전파되고 호출부 래퍼가
 * 모든 RuntimeException 을 PaymentGatewayException(502)으로 일괄 래핑한다. 여기서 직접 던지면 더블래핑되므로 호출부에 위임한다.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"dev", "prod"})
@EnableConfigurationProperties({TossPaymentProperties.class, TossResilienceProperties.class})
public class TossPaymentConfig {

    /** 토스 호출용 Retry·CircuitBreaker 인스턴스 이름(메트릭/로깅 식별자). */
    static final String RESILIENCE_NAME = "tossPayment";

    /**
     * 토스 시크릿 키로 {@code Authorization: Basic base64(secretKey + ":")} 헤더를 주입하는 인터셉터.
     * 시크릿 키 뒤에 콜론을 붙여(빈 패스워드) UTF-8 base64 인코딩하는 토스 인증 규약을 따른다.
     */
    static ClientHttpRequestInterceptor basicAuthInterceptor(String secretKey) {
        String token = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        String headerValue = "Basic " + token;
        return (request, body, execution) -> {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, headerValue);
            return execution.execute(request, body);
        };
    }

    @Bean
    public RestClient tossRestClient(TossPaymentProperties props) {
        // connect 타임아웃은 풀링 HttpClient 에, read 타임아웃은 RequestFactory 에 건다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .requestInterceptor(basicAuthInterceptor(props.secretKey()))
                .build();
    }

    /**
     * 토스 confirm/query/refund 공유 서킷브레이커. 일시 장애(5xx·연결 실패)만 실패로 집계하고,
     * 느린 호출 비율도 함께 본다 — 누적 실패율/느린호출율이 임계를 넘으면 OPEN → 후속 호출 빠른 실패로 워커 점유를 끊는다.
     */
    @Bean
    public CircuitBreaker tossPaymentCircuitBreaker(TossResilienceProperties props) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(props.cbSlidingWindowSize())
                .minimumNumberOfCalls(props.cbMinimumNumberOfCalls())
                .failureRateThreshold(props.cbFailureRateThreshold())
                .slowCallDurationThreshold(props.cbSlowCallDurationThreshold())
                .slowCallRateThreshold(props.cbSlowCallRateThreshold())
                .waitDurationInOpenState(props.cbWaitInOpenState())
                .recordException(TossPaymentGateway::isRetryableTransient)
                .build();
        return CircuitBreaker.of(RESILIENCE_NAME, config);
    }

    /**
     * 토스 confirm/query/refund 공유 재시도. 일시 장애(5xx·연결 실패)만 재시도하며, 동기 confirm 워커 점유·UX 보호를 위해
     * 시도 횟수·백오프를 작게 둔다(읽기 타임아웃은 재시도하지 않음 — 어댑터 술어 참고).
     */
    @Bean
    public Retry tossPaymentRetry(TossResilienceProperties props) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(props.retryMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(props.retryWaitDuration().toMillis(), 2.0))
                .retryOnException(TossPaymentGateway::isRetryableTransient)
                .build();
        return Retry.of(RESILIENCE_NAME, config);
    }
}
