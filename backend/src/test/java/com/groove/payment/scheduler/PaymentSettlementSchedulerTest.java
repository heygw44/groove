package com.groove.payment.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentTransaction;
import com.groove.payment.config.PaymentSettlementProperties;
import com.groove.payment.service.PaymentSettlementLock;
import com.groove.payment.service.PaymentSettlementReport;
import com.groove.payment.service.PaymentSettlementService;

@ExtendWith(MockitoExtension.class)
class PaymentSettlementSchedulerTest {

	@Mock
	private PaymentSettlementService settlementService;

	@Mock
	private PaymentSettlementLock settlementLock;

	@Mock
	private PaymentClient paymentClient;

	@Mock
	private ShutdownSignal shutdownSignal;

	@Mock
	private AlertNotifier alertNotifier;

	private PaymentSettlementScheduler scheduler;

	private Clock clock;
	private LocalDateTime from;
	private LocalDateTime to;

	@BeforeEach
	void setUp() {
		// 자정(2026-09-22T00:00 KST) 직후 고정 - 오늘 00:00~어제 00:00 구간을 대사한다.
		clock = Clock.fixed(Instant.parse("2026-09-21T20:10:00Z"), ZoneId.of("Asia/Seoul"));
		to = LocalDateTime.of(2026, 9, 22, 0, 0, 0);
		from = to.minusDays(1);
		PaymentSettlementProperties settlementProperties = new PaymentSettlementProperties("0 10 5 * * *",
				Duration.ofMinutes(10), 5000, 20);
		scheduler = new PaymentSettlementScheduler(settlementService, settlementLock, paymentClient,
				settlementProperties, shutdownSignal, clock, alertNotifier);
	}

	@Nested
	@DisplayName("settle()")
	class Settle {

		@Test
		@DisplayName("overlap 만큼 넓힌 구간으로 거래를 조회하고, 대사 자체는 넓히지 않은 구간으로 넘긴다")
		void passesOverlapToLookupButNotToReconcile() {
			// given
			stubLockToRunTask();
			List<PaymentTransaction> transactions = List.of();
			given(paymentClient.listTransactions(from.minusMinutes(10), to.plusMinutes(10)))
					.willReturn(transactions);
			given(settlementService.reconcile(transactions, from, to))
					.willReturn(new PaymentSettlementReport(0, 0, 0, 0, 0, 0));

			// when
			scheduler.settle();

			// then
			verify(paymentClient).listTransactions(from.minusMinutes(10), to.plusMinutes(10));
			verify(settlementService).reconcile(transactions, from, to);
		}

		@Test
		@DisplayName("거래 조회가 실패하면 경보를 보내고 대사를 진행하지 않는다")
		void notifiesAlertWhenLookupFails() {
			// given
			stubLockToRunTask();
			willThrow(new RuntimeException("TOSS 통신 실패")).given(paymentClient)
					.listTransactions(from.minusMinutes(10), to.plusMinutes(10));

			// when
			scheduler.settle();

			// then
			verify(alertNotifier).notify(any(Alert.class));
			verify(settlementService, never()).reconcile(any(), any(), any());
		}

		@Test
		@DisplayName("락 획득에 실패하면 아무것도 하지 않는다")
		void doesNothingWhenLockAcquisitionFails() {
			// given
			given(settlementLock.runExclusively(any())).willReturn(false);

			// when
			scheduler.settle();

			// then
			verify(paymentClient, never()).listTransactions(any(), any());
		}

		@Test
		@DisplayName("셧다운 중이면 락도 잡지 않는다")
		void doesNotAcquireLockWhenShuttingDown() {
			// given
			given(shutdownSignal.isShuttingDown()).willReturn(true);

			// when
			scheduler.settle();

			// then
			verify(settlementLock, never()).runExclusively(any());
		}
	}

	private void stubLockToRunTask() {
		given(settlementLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}
}
