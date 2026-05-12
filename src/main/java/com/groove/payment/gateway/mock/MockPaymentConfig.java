package com.groove.payment.gateway.mock;

import com.groove.payment.gateway.PaymentMockProperties;
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
 *   <li>{@code paymentTaskScheduler} — {@link MockWebhookSimulator} 가 웹훅 콜백을 일회성 지연 실행하는 전용
 *       {@link TaskScheduler}. 앱 전역 {@code @EnableScheduling}(폴링 스케줄러용)은 {@code common.scheduling.SchedulingConfig}.</li>
 * </ul>
 *
 * <p>{@code WebhookDispatcher} 구현({@code PaymentWebhookHandler}, #W7-4)과 서명 검증기
 * ({@code MockWebhookSignatureVerifier}, #W7-4)는 같은 프로파일에서 {@code @Component} 로 스캔된다.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev", "test", "docker"})
@EnableConfigurationProperties(PaymentMockProperties.class)
public class MockPaymentConfig {

    /** 웹훅 콜백 전용 스케줄러 풀 크기 — 일회성 단발 작업이라 작게 잡는다. */
    private static final int WEBHOOK_SCHEDULER_POOL_SIZE = 2;
    /** 종료 시 진행 중 콜백을 기다리는 최대 시간(초). */
    private static final int WEBHOOK_SCHEDULER_AWAIT_TERMINATION_SECONDS = 5;
    private static final String WEBHOOK_SCHEDULER_THREAD_PREFIX = "payment-webhook-";

    @Bean(name = "paymentTaskScheduler")
    public TaskScheduler paymentTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(WEBHOOK_SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix(WEBHOOK_SCHEDULER_THREAD_PREFIX);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(WEBHOOK_SCHEDULER_AWAIT_TERMINATION_SECONDS);
        scheduler.initialize();
        return scheduler;
    }
}
