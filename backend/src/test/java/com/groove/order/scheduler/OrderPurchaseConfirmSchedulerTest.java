package com.groove.order.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.alert.AlertSeverity;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.order.config.OrderPurchaseConfirmProperties;
import com.groove.order.dto.OrderItemConfirmCandidate;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.service.OrderPurchaseConfirmLock;
import com.groove.order.service.OrderPurchaseConfirmService;

@ExtendWith(MockitoExtension.class)
class OrderPurchaseConfirmSchedulerTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final LocalDateTime INITIAL_CURSOR_DELIVERED_AT = LocalDateTime.of(1970, 1, 1, 0, 0);

	@Mock
	private OrderItemRepository orderItemRepository;

	@Mock
	private OrderPurchaseConfirmService orderPurchaseConfirmService;

	@Mock
	private OrderPurchaseConfirmLock orderPurchaseConfirmLock;

	@Mock
	private ShutdownSignal shutdownSignal;

	@Mock
	private AlertNotifier alertNotifier;

	private OrderPurchaseConfirmScheduler scheduler;
	private Clock clock;

	private LocalDateTime now;
	private LocalDateTime cutoff;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-04T03:30:00Z"), ZONE);
		now = LocalDateTime.now(clock);
		OrderPurchaseConfirmProperties properties = new OrderPurchaseConfirmProperties(8, "0 30 3 * * *", 200);
		cutoff = now.minusDays(properties.days());
		scheduler = new OrderPurchaseConfirmScheduler(orderItemRepository, orderPurchaseConfirmService,
				orderPurchaseConfirmLock, properties, shutdownSignal, clock, alertNotifier);
	}

	@Nested
	@DisplayName("confirmPurchases()")
	class ConfirmPurchases {

		@Test
		@DisplayName("시작 시 셧다운 중이면 락도 잡지 않는다")
		void doesNotAcquireLockWhenShuttingDownAtStart() {
			// given
			given(shutdownSignal.isShuttingDown()).willReturn(true);

			// when
			scheduler.confirmPurchases();

			// then
			verify(orderPurchaseConfirmLock, never()).runExclusively(any());
		}

		@Test
		@DisplayName("락 획득에 실패하면 대상 조회조차 하지 않는다")
		void doesNothingWhenLockAcquisitionFails() {
			// given
			given(shutdownSignal.isShuttingDown()).willReturn(false);
			given(orderPurchaseConfirmLock.runExclusively(any())).willReturn(false);

			// when
			scheduler.confirmPurchases();

			// then
			verify(orderItemRepository, never()).findConfirmCandidates(any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("대상이 없으면 서비스를 호출하지 않는다")
		void doesNotCallServiceWhenNoCandidates() {
			// given
			stubLockToRunTask();
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(),
					eq(INITIAL_CURSOR_DELIVERED_AT), eq(0L), any())).willReturn(List.of());

			// when
			scheduler.confirmPurchases();

			// then
			verify(orderPurchaseConfirmService, never()).confirm(anyLong(), any(), any());
			verify(alertNotifier, never()).notify(any());
		}

		@Test
		@DisplayName("한 건이 실패해도 나머지 후보는 계속 처리한다")
		void continuesProcessingWhenOneItemFails() {
			// given
			stubLockToRunTask();
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(), any(),
					any(), any())).willReturn(candidates(1L, 2L, 3L));
			given(orderPurchaseConfirmService.confirm(1L, cutoff, now)).willReturn(false);
			given(orderPurchaseConfirmService.confirm(2L, cutoff, now)).willThrow(new RuntimeException("boom"));
			given(orderPurchaseConfirmService.confirm(3L, cutoff, now)).willReturn(true);

			// when
			scheduler.confirmPurchases();

			// then
			verify(orderPurchaseConfirmService).confirm(1L, cutoff, now);
			verify(orderPurchaseConfirmService).confirm(2L, cutoff, now);
			verify(orderPurchaseConfirmService).confirm(3L, cutoff, now);
		}

		@Test
		@DisplayName("첫 건 처리 후 셧다운 신호가 오면 두 번째 건을 처리하지 않는다")
		void stopsProcessingWhenShutdownSignaledMidLoop() {
			// given
			stubLockToRunTask();
			given(shutdownSignal.isShuttingDown()).willReturn(false, false, false, true);
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(), any(),
					any(), any())).willReturn(candidates(1L, 2L));
			given(orderPurchaseConfirmService.confirm(1L, cutoff, now)).willReturn(true);

			// when
			scheduler.confirmPurchases();

			// then
			verify(orderPurchaseConfirmService).confirm(1L, cutoff, now);
			verify(orderPurchaseConfirmService, never()).confirm(eq(2L), any(), any());
		}

		@Test
		@DisplayName("한 바퀴가 배치 크기를 꽉 채우면 마지막 후보 뒤로 커서를 옮겨 다시 조회한다")
		void requeriesAfterLastCandidateWhenBatchIsFull() {
			// given
			stubLockToRunTask();
			OrderPurchaseConfirmScheduler small = schedulerWithBatchSize(2);
			List<OrderItemConfirmCandidate> firstPage = candidates(1L, 2L);
			OrderItemConfirmCandidate last = firstPage.get(1);
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(),
					eq(INITIAL_CURSOR_DELIVERED_AT), eq(0L), any())).willReturn(firstPage);
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(),
					eq(last.deliveredAt()), eq(last.id()), any())).willReturn(candidates(3L));
			given(orderPurchaseConfirmService.confirm(anyLong(), eq(cutoff), eq(now))).willReturn(true);

			// when
			small.confirmPurchases();

			// then
			verify(orderItemRepository, times(2)).findConfirmCandidates(any(), any(), any(), any(), any(), any());
			verify(orderPurchaseConfirmService).confirm(3L, cutoff, now);
		}

		@Test
		@DisplayName("앞 배치가 모두 실패해도 커서를 넘겨 뒤의 정상 건을 처리한다")
		void processesLaterCandidatesWhenWholeBatchFails() {
			// given
			stubLockToRunTask();
			OrderPurchaseConfirmScheduler small = schedulerWithBatchSize(2);
			List<OrderItemConfirmCandidate> firstPage = candidates(1L, 2L);
			OrderItemConfirmCandidate last = firstPage.get(1);
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(),
					eq(INITIAL_CURSOR_DELIVERED_AT), eq(0L), any())).willReturn(firstPage);
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(),
					eq(last.deliveredAt()), eq(last.id()), any())).willReturn(candidates(3L));
			given(orderPurchaseConfirmService.confirm(1L, cutoff, now)).willThrow(new RuntimeException("boom"));
			given(orderPurchaseConfirmService.confirm(2L, cutoff, now)).willThrow(new RuntimeException("boom"));
			given(orderPurchaseConfirmService.confirm(3L, cutoff, now)).willReturn(true);

			// when
			small.confirmPurchases();

			// then
			verify(orderPurchaseConfirmService).confirm(3L, cutoff, now);
		}

		@Test
		@DisplayName("배치 크기보다 적게 조회되면 한 번만 조회하고 끝낸다")
		void queriesOnceWhenBatchIsPartial() {
			// given
			stubLockToRunTask();
			OrderPurchaseConfirmScheduler small = schedulerWithBatchSize(2);
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(), any(),
					any(), any())).willReturn(candidates(1L));
			given(orderPurchaseConfirmService.confirm(1L, cutoff, now)).willReturn(true);

			// when
			small.confirmPurchases();

			// then
			verify(orderItemRepository, times(1)).findConfirmCandidates(any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("계속 가득 찬 배치가 나와도 최대 바퀴 수에서 멈춘다")
		void stopsAtMaxRounds() {
			// given
			stubLockToRunTask();
			OrderPurchaseConfirmScheduler small = schedulerWithBatchSize(2);
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(), any(),
					any(), any())).willReturn(candidates(1L, 2L));
			given(orderPurchaseConfirmService.confirm(anyLong(), eq(cutoff), eq(now))).willReturn(true);

			// when
			small.confirmPurchases();

			// then
			verify(orderItemRepository, times(50)).findConfirmCandidates(any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("예외로 실패한 건이 있으면 실패 id 를 담아 CRITICAL 알림을 한 번 보낸다")
		void sendsSingleCriticalAlertWhenItemsFail() {
			// given
			stubLockToRunTask();
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(), any(),
					any(), any())).willReturn(candidates(1L, 2L, 3L));
			given(orderPurchaseConfirmService.confirm(1L, cutoff, now)).willThrow(new RuntimeException("boom"));
			given(orderPurchaseConfirmService.confirm(2L, cutoff, now)).willReturn(true);
			given(orderPurchaseConfirmService.confirm(3L, cutoff, now)).willThrow(new RuntimeException("boom"));

			// when
			scheduler.confirmPurchases();

			// then
			ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
			verify(alertNotifier, times(1)).notify(captor.capture());
			Alert alert = captor.getValue();
			assertThat(alert.severity()).isEqualTo(AlertSeverity.CRITICAL);
			assertThat(alert.key()).isEqualTo("order.purchase-confirm-failed");
			assertThat(alert.summary()).isEqualTo("자동 구매확정 실패 2건: orderItemIds=[1, 3]");
			assertThat(alert.targetId()).isEqualTo("orderItemId=1");
		}

		@Test
		@DisplayName("실패가 10건을 넘으면 앞 10건만 나열하고 나머지는 건수로 알린다")
		void truncatesFailedIdsInAlertSummary() {
			// given
			stubLockToRunTask();
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(), any(),
					any(), any())).willReturn(candidates(LongStream.rangeClosed(1, 12).toArray()));
			given(orderPurchaseConfirmService.confirm(anyLong(), eq(cutoff), eq(now)))
					.willThrow(new RuntimeException("boom"));

			// when
			scheduler.confirmPurchases();

			// then
			ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
			verify(alertNotifier).notify(captor.capture());
			assertThat(captor.getValue().summary())
					.isEqualTo("자동 구매확정 실패 12건: orderItemIds=[1, 2, 3, 4, 5, 6, 7, 8, 9, 10] 외 2건");
		}

		@Test
		@DisplayName("건너뛴 건만 있고 실패가 없으면 알림을 보내지 않는다")
		void doesNotAlertWhenOnlySkipped() {
			// given
			stubLockToRunTask();
			given(orderItemRepository.findConfirmCandidates(eq(OrderItemStatus.DELIVERED), eq(cutoff), any(), any(),
					any(), any())).willReturn(candidates(1L, 2L));
			given(orderPurchaseConfirmService.confirm(1L, cutoff, now)).willReturn(false);
			given(orderPurchaseConfirmService.confirm(2L, cutoff, now)).willReturn(true);

			// when
			scheduler.confirmPurchases();

			// then
			verify(alertNotifier, never()).notify(any());
		}
	}

	private List<OrderItemConfirmCandidate> candidates(long... ids) {
		List<OrderItemConfirmCandidate> result = new ArrayList<>();
		for (long id : ids) {
			result.add(new OrderItemConfirmCandidate(id, cutoff.minusDays(1).plusMinutes(id)));
		}
		return result;
	}

	private OrderPurchaseConfirmScheduler schedulerWithBatchSize(int batchSize) {
		return new OrderPurchaseConfirmScheduler(orderItemRepository, orderPurchaseConfirmService,
				orderPurchaseConfirmLock, new OrderPurchaseConfirmProperties(8, "0 30 3 * * *", batchSize),
				shutdownSignal, clock, alertNotifier);
	}

	private void stubLockToRunTask() {
		given(orderPurchaseConfirmLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}
}
