package com.groove.order.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.groove.order.dto.OrderExpirationCandidate;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderExpirationLock;
import com.groove.order.service.OrderExpirationService;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final LocalDateTime INITIAL_CURSOR_EXPIRES_AT = LocalDateTime.of(1970, 1, 1, 0, 0);

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderExpirationService orderExpirationService;

	@Mock
	private OrderExpirationLock orderExpirationLock;

	@Mock
	private ShutdownSignal shutdownSignal;

	@Mock
	private AlertNotifier alertNotifier;

	private OrderExpirationScheduler scheduler;

	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		now = LocalDateTime.now(clock);
		scheduler = new OrderExpirationScheduler(orderRepository, orderExpirationService, orderExpirationLock,
				shutdownSignal, clock, alertNotifier);
	}

	@Nested
	@DisplayName("expireOrders()")
	class ExpireOrders {

		@Test
		@DisplayName("시작 시 셧다운 중이면 락도 잡지 않는다")
		void doesNotAcquireLockWhenShuttingDownAtStart() {
			// given
			given(shutdownSignal.isShuttingDown()).willReturn(true);

			// when
			scheduler.expireOrders();

			// then
			verify(orderExpirationLock, never()).runExclusively(any());
		}

		@Test
		@DisplayName("락 획득에 실패하면 대상 조회조차 하지 않는다")
		void doesNothingWhenLockAcquisitionFails() {
			// given
			given(shutdownSignal.isShuttingDown()).willReturn(false);
			given(orderExpirationLock.runExclusively(any())).willReturn(false);

			// when
			scheduler.expireOrders();

			// then
			verify(orderRepository, never()).findExpirationCandidates(any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("만료 대상이 없으면 서비스를 호출하지 않는다")
		void doesNotCallServiceWhenNoCandidates() {
			// given
			stubLockToRunTask();
			given(orderRepository.findExpirationCandidates(eq(OrderStatus.PENDING), eq(now), any(),
					eq(INITIAL_CURSOR_EXPIRES_AT), eq(0L), any())).willReturn(List.of());

			// when
			scheduler.expireOrders();

			// then
			verify(orderExpirationService, never()).expire(anyLong(), any());
			verify(alertNotifier, never()).notify(any());
		}

		@Test
		@DisplayName("한 건이 실패해도 나머지 후보는 계속 처리한다")
		void continuesProcessingWhenOneOrderFails() {
			// given
			stubLockToRunTask();
			given(orderRepository.findExpirationCandidates(eq(OrderStatus.PENDING), eq(now), any(), any(), any(),
					any())).willReturn(candidates(1L, 2L, 3L));
			given(orderExpirationService.expire(1L, now)).willReturn(false);
			given(orderExpirationService.expire(2L, now)).willThrow(new RuntimeException("boom"));
			given(orderExpirationService.expire(3L, now)).willReturn(true);

			// when
			scheduler.expireOrders();

			// then
			verify(orderExpirationService).expire(1L, now);
			verify(orderExpirationService).expire(2L, now);
			verify(orderExpirationService).expire(3L, now);
		}

		@Test
		@DisplayName("첫 건 처리 후 셧다운 신호가 오면 두 번째 건을 처리하지 않는다")
		void stopsProcessingWhenShutdownSignaledMidLoop() {
			// given
			stubLockToRunTask();
			given(shutdownSignal.isShuttingDown()).willReturn(false, false, false, true);
			given(orderRepository.findExpirationCandidates(eq(OrderStatus.PENDING), eq(now), any(), any(), any(),
					any())).willReturn(candidates(1L, 2L));
			given(orderExpirationService.expire(1L, now)).willReturn(true);

			// when
			scheduler.expireOrders();

			// then
			verify(orderExpirationService).expire(1L, now);
			verify(orderExpirationService, never()).expire(eq(2L), any());
		}

		@Test
		@DisplayName("다음 바퀴 시작 전에 셧다운 신호가 오면 더 조회하지 않는다")
		void stopsQueryingWhenShutdownSignaledBetweenRounds() {
			// given
			stubLockToRunTask();
			List<OrderExpirationCandidate> fullPage = fullPage();
			// 시작·첫 바퀴·후보마다 한 번씩 false 를 본 뒤, 두 번째 바퀴 시작에서 셧다운을 본다.
			Boolean[] rest = new Boolean[fullPage.size() + 2];
			Arrays.fill(rest, Boolean.FALSE);
			rest[rest.length - 1] = Boolean.TRUE;
			given(shutdownSignal.isShuttingDown()).willReturn(false, rest);
			given(orderRepository.findExpirationCandidates(eq(OrderStatus.PENDING), eq(now), any(), any(), any(),
					any())).willReturn(fullPage);
			given(orderExpirationService.expire(anyLong(), eq(now))).willReturn(true);

			// when
			scheduler.expireOrders();

			// then
			verify(orderRepository, times(1)).findExpirationCandidates(any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("앞 배치가 모두 실패해도 마지막 후보 뒤로 커서를 옮겨 뒤의 정상 주문을 만료한다")
		void expiresLaterOrderWhenWholeBatchFails() {
			// given
			stubLockToRunTask();
			List<OrderExpirationCandidate> firstPage = fullPage();
			OrderExpirationCandidate last = firstPage.get(firstPage.size() - 1);
			long healthyId = last.id() + 1;
			given(orderRepository.findExpirationCandidates(eq(OrderStatus.PENDING), eq(now), any(),
					eq(INITIAL_CURSOR_EXPIRES_AT), eq(0L), any())).willReturn(firstPage);
			given(orderRepository.findExpirationCandidates(eq(OrderStatus.PENDING), eq(now), any(),
					eq(last.expiresAt()), eq(last.id()), any())).willReturn(candidates(healthyId));
			given(orderExpirationService.expire(anyLong(), eq(now))).willThrow(new RuntimeException("boom"));
			willReturn(true).given(orderExpirationService).expire(healthyId, now);

			// when
			scheduler.expireOrders();

			// then
			verify(orderRepository, times(2)).findExpirationCandidates(any(), any(), any(), any(), any(), any());
			verify(orderExpirationService).expire(healthyId, now);
		}

		@Test
		@DisplayName("배치 크기보다 적게 조회되면 한 번만 조회하고 끝낸다")
		void queriesOnceWhenBatchIsPartial() {
			// given
			stubLockToRunTask();
			given(orderRepository.findExpirationCandidates(eq(OrderStatus.PENDING), eq(now), any(), any(), any(),
					any())).willReturn(candidates(1L));
			given(orderExpirationService.expire(1L, now)).willReturn(true);

			// when
			scheduler.expireOrders();

			// then
			verify(orderRepository, times(1)).findExpirationCandidates(any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("계속 가득 찬 배치가 나와도 최대 바퀴 수에서 멈춘다")
		void stopsAtMaxRounds() {
			// given
			stubLockToRunTask();
			given(orderRepository.findExpirationCandidates(eq(OrderStatus.PENDING), eq(now), any(), any(), any(),
					any())).willReturn(fullPage());
			given(orderExpirationService.expire(anyLong(), eq(now))).willReturn(false);

			// when
			scheduler.expireOrders();

			// then
			verify(orderRepository, times(50)).findExpirationCandidates(any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("예외로 실패한 건이 있으면 실패 id 를 담아 CRITICAL 알림을 한 번 보낸다")
		void sendsSingleCriticalAlertWhenOrdersFail() {
			// given
			stubLockToRunTask();
			given(orderRepository.findExpirationCandidates(eq(OrderStatus.PENDING), eq(now), any(), any(), any(),
					any())).willReturn(candidates(1L, 2L, 3L));
			given(orderExpirationService.expire(1L, now)).willThrow(new RuntimeException("boom"));
			given(orderExpirationService.expire(2L, now)).willReturn(true);
			given(orderExpirationService.expire(3L, now)).willThrow(new RuntimeException("boom"));

			// when
			scheduler.expireOrders();

			// then
			ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
			verify(alertNotifier, times(1)).notify(captor.capture());
			Alert alert = captor.getValue();
			assertThat(alert.severity()).isEqualTo(AlertSeverity.CRITICAL);
			assertThat(alert.key()).isEqualTo("order.expiration-failed");
			assertThat(alert.summary()).isEqualTo("주문 만료 실패 2건: orderIds=[1, 3]");
			assertThat(alert.targetId()).isEqualTo("orderId=1");
		}

		@Test
		@DisplayName("실패가 10건을 넘으면 앞 10건만 나열하고 나머지는 건수로 알린다")
		void truncatesFailedIdsInAlertSummary() {
			// given
			stubLockToRunTask();
			given(orderRepository.findExpirationCandidates(eq(OrderStatus.PENDING), eq(now), any(), any(), any(),
					any())).willReturn(candidates(LongStream.rangeClosed(1, 12).toArray()));
			given(orderExpirationService.expire(anyLong(), eq(now))).willThrow(new RuntimeException("boom"));

			// when
			scheduler.expireOrders();

			// then
			ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
			verify(alertNotifier).notify(captor.capture());
			assertThat(captor.getValue().summary())
					.isEqualTo("주문 만료 실패 12건: orderIds=[1, 2, 3, 4, 5, 6, 7, 8, 9, 10] 외 2건");
		}

		@Test
		@DisplayName("만료하지 않고 건너뛴 건만 있고 실패가 없으면 알림을 보내지 않는다")
		void doesNotAlertWhenOnlySkipped() {
			// given
			stubLockToRunTask();
			given(orderRepository.findExpirationCandidates(eq(OrderStatus.PENDING), eq(now), any(), any(), any(),
					any())).willReturn(candidates(1L, 2L));
			given(orderExpirationService.expire(1L, now)).willReturn(false);
			given(orderExpirationService.expire(2L, now)).willReturn(true);

			// when
			scheduler.expireOrders();

			// then
			verify(alertNotifier, never()).notify(any());
		}
	}

	private List<OrderExpirationCandidate> candidates(long... ids) {
		List<OrderExpirationCandidate> result = new ArrayList<>();
		for (long id : ids) {
			result.add(new OrderExpirationCandidate(id, now.minusHours(1).plusSeconds(id)));
		}
		return result;
	}

	private List<OrderExpirationCandidate> fullPage() {
		return candidates(LongStream.rangeClosed(1, OrderExpirationScheduler.BATCH_SIZE).toArray());
	}

	private void stubLockToRunTask() {
		given(orderExpirationLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}
}
