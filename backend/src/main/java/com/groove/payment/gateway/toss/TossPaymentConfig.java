package com.groove.payment.gateway.toss;

import com.groove.payment.gateway.TossPaymentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 토스페이먼츠 연동 구성(#293) — 코드베이스 최초의 외부 HTTP 클라이언트.
 *
 * <p>dev/prod 프로파일에서만 활성화되며, test/local/docker 는 기존 MockPaymentGateway 를 그대로 쓴다.
 * 실 PG 어댑터(TossPaymentGateway)와 confirm 흐름·웹훅은 다음 이슈에서 이 RestClient 빈을 주입해 구현한다.
 *
 * <p><b>4xx/5xx → 502 매핑 정책:</b> 이 RestClient 에는 커스텀 status handler 를 두지 않는다.
 * 토스 오류 응답은 RestClient 기본 동작대로 {@code RestClientResponseException}(RuntimeException)으로 전파되고,
 * 결제 호출부(PaymentService#callGateway, GatewayRefunds#refund)가 이미 모든 RuntimeException 을
 * {@code PaymentGatewayException}(HTTP 502)으로 일괄 래핑한다. 따라서 여기서 PaymentGatewayException 을
 * 직접 던지면 호출부에서 더블래핑되므로 던지지 않는다 — 502 변환은 호출부 래퍼에 위임한다.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"dev", "prod"})
@EnableConfigurationProperties(TossPaymentProperties.class)
public class TossPaymentConfig {

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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .requestInterceptor(basicAuthInterceptor(props.secretKey()))
                .build();
    }
}
