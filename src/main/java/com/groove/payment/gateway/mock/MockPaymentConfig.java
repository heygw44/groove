package com.groove.payment.gateway.mock;

import com.groove.payment.gateway.PaymentMockProperties;
import com.groove.payment.gateway.WebhookDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Mock PG 구성 (W7-1).
 *
 * <p>{@code @Profile} 로 Mock 구현체와 함께 격리된다 — 실 PG 프로파일에서는 로드되지 않으므로
 * {@code payment.mock.*} 프로퍼티 바인딩과 전용 스케줄러도 그 프로파일에는 존재하지 않는다.
 *
 * <p>제공 빈:
 * <ul>
 *   <li>{@code paymentTaskScheduler} — 웹훅 콜백 일회성 지연 실행 전용 {@link TaskScheduler}.
 *       앱 전역 {@code @EnableScheduling} 은 폴링 스케줄러가 도입되는 #W7-4 에서 추가한다.</li>
 *   <li>{@link WebhookDispatcher} 기본 구현({@link LoggingWebhookDispatcher}) — #W7-4 가 실제
 *       수신 핸들러를 빈으로 등록하면 {@code @ConditionalOnMissingBean} 에 의해 대체된다.
 *       단 {@code @ConditionalOnMissingBean} 은 빈 등록 순서에 민감하므로, #W7-4 의 수신 핸들러는
 *       {@code @Primary} 로 등록하거나 본 기본 빈을 제거해 모호성을 피해야 한다.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev", "test", "docker"})
@EnableConfigurationProperties(PaymentMockProperties.class)
public class MockPaymentConfig {

    @Bean(name = "paymentTaskScheduler")
    public TaskScheduler paymentTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("payment-webhook-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean(WebhookDispatcher.class)
    public WebhookDispatcher loggingWebhookDispatcher() {
        return new LoggingWebhookDispatcher();
    }
}
