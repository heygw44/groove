package com.groove.payment.gateway.mock;

import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.gateway.PaymentMockProperties;
import com.groove.payment.gateway.WebhookDispatcher;
import com.groove.payment.gateway.WebhookNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MockWebhookSimulator 단위 테스트")
class MockWebhookSimulatorTest {

    private static final Instant NOW = Instant.parse("2026-05-12T10:00:00Z");

    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private WebhookDispatcher dispatcher;
    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final PaymentMockProperties properties = new PaymentMockProperties(
            1.0, Duration.ZERO, Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(5), "test-secret");

    private MockWebhookSimulator simulator;

    @BeforeEach
    void setUp() {
        simulator = new MockWebhookSimulator(taskScheduler, dispatcher, properties, clock);
    }

    @Test
    @DisplayName("scheduleCallback: 현재 시각 + delay 시점에 작업을 예약한다")
    void scheduleCallback_schedulesAtNowPlusDelay() {
        simulator.scheduleCallback("mock-tx-1", "ORD-1", PaymentStatus.PAID, Duration.ofSeconds(3));

        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.plusSeconds(3)));
        then(dispatcher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("예약된 작업 실행 시 올바른 페이로드로 디스패처를 호출한다")
    void scheduledTask_dispatchesNotificationWithExpectedPayload() {
        simulator.scheduleCallback("mock-tx-2", "ORD-2", PaymentStatus.FAILED, Duration.ZERO);
        verify(taskScheduler).schedule(runnableCaptor.capture(), any(Instant.class));

        runnableCaptor.getValue().run();

        ArgumentCaptor<WebhookNotification> notification = ArgumentCaptor.forClass(WebhookNotification.class);
        verify(dispatcher).dispatch(notification.capture());
        WebhookNotification sent = notification.getValue();
        assertThat(sent.pgTransactionId()).isEqualTo("mock-tx-2");
        assertThat(sent.orderNumber()).isEqualTo("ORD-2");
        assertThat(sent.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(sent.occurredAt()).isEqualTo(NOW);
        assertThat(sent.signature()).isEqualTo("test-secret");
    }

    @Test
    @DisplayName("디스패처가 예외를 던져도 예약 작업 밖으로 전파하지 않는다")
    void scheduledTask_swallowsDispatcherException() {
        willThrow(new RuntimeException("downstream down")).given(dispatcher).dispatch(any());
        simulator.scheduleCallback("mock-tx-3", "ORD-3", PaymentStatus.PAID, Duration.ZERO);
        verify(taskScheduler).schedule(runnableCaptor.capture(), any(Instant.class));

        assertThatCode(() -> runnableCaptor.getValue().run()).doesNotThrowAnyException();
        verify(dispatcher).dispatch(any());
    }

    @Test
    @DisplayName("null delay 는 거부한다")
    void nullDelay_rejected() {
        assertThatCode(() -> simulator.scheduleCallback("tx", "ORD", PaymentStatus.PAID, null))
                .isInstanceOf(NullPointerException.class);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
