package com.groove.payment.gateway.mock;

import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.PaymentMockProperties;
import com.groove.payment.gateway.PaymentRequest;
import com.groove.payment.gateway.WebhookDispatcher;
import com.groove.payment.gateway.WebhookNotification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mock PG 구성 빈 와이어링 + 비동기 웹훅 발사 통합 검증.
 *
 * <p>{@code @SpringBootTest(classes=...)} 로 자동 구성 없이 결제 게이트웨이 구성만 띄운다
 * (DB·Flyway·웹 미기동). {@code test} 프로파일에서 {@code @Profile} 격리 빈들이 실제로 로드되고
 * {@code payment.mock.*} 가 바인딩되는지, 그리고 {@code request()} → 비동기 웹훅 콜백 경로가
 * 실제 {@code TaskScheduler} 위에서 동작하는지 확인한다 (#W7-1 DoD).
 */
@SpringBootTest(
        classes = {
                MockPaymentConfig.class,
                MockPaymentGateway.class,
                MockWebhookSimulator.class,
                MockPaymentWiringTest.TestSupportConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DisplayName("MockPayment 구성 와이어링 통합 테스트")
class MockPaymentWiringTest {

    @Autowired
    private ApplicationContext context;
    @Autowired
    private PaymentGateway paymentGateway;
    @Autowired
    private PaymentMockProperties properties;
    @Autowired
    private CapturingWebhookDispatcher dispatcher;

    @Test
    @DisplayName("test 프로파일에서 Mock 게이트웨이·전용 스케줄러 빈이 로드되고 properties 가 바인딩된다")
    void wiresMockGatewayBeans() {
        assertThat(paymentGateway).isInstanceOf(MockPaymentGateway.class);
        assertThat(context.containsBean("paymentTaskScheduler")).isTrue();
        assertThat(context.getBean(WebhookDispatcher.class)).isSameAs(dispatcher);
        assertThat(properties.successRate()).isEqualTo(1.0);
        assertThat(properties.webhookSecret()).isEqualTo("test-mock-webhook-secret");
    }

    @Test
    @DisplayName("request() 호출 후 비동기 웹훅 콜백이 PAID 페이로드로 도착한다")
    void requestTriggersAsyncWebhook() {
        dispatcher.reset();

        var response = paymentGateway.request(new PaymentRequest("ORD-20260512-WIRE01", 42_000L));

        // 테스트 프로파일은 지연 0ms 라 사실상 즉시 발사되지만, CI 부하 시 스케줄링 지터에 여유를 둔다.
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            WebhookNotification n = dispatcher.last();
            assertThat(n).isNotNull();
            assertThat(n.pgTransactionId()).isEqualTo(response.pgTransactionId());
            assertThat(n.orderNumber()).isEqualTo("ORD-20260512-WIRE01");
            assertThat(n.status().name()).isEqualTo("PAID"); // application-test.yaml: success-rate=1.0
            assertThat(n.signature()).isEqualTo("test-mock-webhook-secret");
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSupportConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        @Primary
        CapturingWebhookDispatcher capturingWebhookDispatcher() {
            return new CapturingWebhookDispatcher();
        }
    }

    static final class CapturingWebhookDispatcher implements WebhookDispatcher {
        private final List<WebhookNotification> received = new CopyOnWriteArrayList<>();

        @Override
        public void dispatch(WebhookNotification notification) {
            received.add(notification);
        }

        void reset() {
            received.clear();
        }

        WebhookNotification last() {
            return received.isEmpty() ? null : received.get(received.size() - 1);
        }
    }
}
