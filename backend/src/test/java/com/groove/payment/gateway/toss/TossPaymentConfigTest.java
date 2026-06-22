package com.groove.payment.gateway.toss;

import com.groove.payment.gateway.TossPaymentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("TossPaymentConfig — RestClient 빈 / Basic Auth 인터셉터")
class TossPaymentConfigTest {

    private static final ApplicationContextRunner CONTEXT = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(TossPaymentConfig.class))
            .withPropertyValues(
                    "payment.toss.client-key=test_ck_x",
                    "payment.toss.secret-key=test_sk_x",
                    "payment.toss.success-url=http://localhost:8080/success",
                    "payment.toss.fail-url=http://localhost:8080/fail");

    private static TossPaymentProperties validProps() {
        return new TossPaymentProperties(
                "https://api.tosspayments.com",
                "test_ck_abc", "test_sk_abc",
                "http://localhost:8080/success", "http://localhost:8080/fail",
                Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Basic Auth 인터셉터는 secretKey + ':' 를 base64 인코딩해 Authorization 헤더로 넣는다")
    void basicAuthInterceptor_encodesSecretKeyWithColon() throws Exception {
        ClientHttpRequestInterceptor interceptor = TossPaymentConfig.basicAuthInterceptor("test_sk_abc");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, URI.create("/v1/payments/confirm"));
        ClientHttpRequestExecution execution =
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);

        interceptor.intercept(request, new byte[0], execution);

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertThat(header).isEqualTo("Basic dGVzdF9za19hYmM6");
        // 콜론(빈 패스워드)을 포함한 시크릿 키가 그대로 디코딩되는지 확인 — 토스 인증 규약
        String decoded = new String(
                Base64.getDecoder().decode(header.substring("Basic ".length())), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("test_sk_abc:");
    }

    @Test
    @DisplayName("빌드된 tossRestClient 로 요청하면 base-url 과 Basic Auth 헤더가 실제 전송된다 (인터셉터 부착 회귀 방지)")
    void tossRestClient_sendsBaseUrlAndAuthHeader() {
        // client.mutate() 로 인터셉터·baseUrl 을 보존한 채 MockRestServiceServer 를 끼워 실제 전송 경로를 검증한다.
        // .requestInterceptor(...) 가 빌더에서 빠지면 이 단언이 깨진다.
        RestClient.Builder rebound = new TossPaymentConfig().tossRestClient(validProps()).mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rebound).build();
        RestClient client = rebound.build();

        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dGVzdF9za19hYmM6"))
                .andRespond(withSuccess());

        client.post().uri("/v1/payments/confirm").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    @DisplayName("dev 프로파일에서 tossRestClient 빈이 구성된다 (완료조건)")
    void devProfile_registersBean() {
        CONTEXT.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("dev"))
                .run(context -> {
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context).hasBean("tossRestClient");
                });
    }

    @Test
    @DisplayName("프로파일 미활성(test/local 상당)에서는 tossRestClient 빈이 등록되지 않는다")
    void noProfile_doesNotRegisterBean() {
        CONTEXT.run(context -> {
            assertThat(context).doesNotHaveBean(RestClient.class);
            assertThat(context).doesNotHaveBean("tossRestClient");
        });
    }
}
