package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.alert.AlertSeverity;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.config.PaymentReconcileProperties;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentReconcileAction;
import com.groove.payment.entity.PaymentReconcileLog;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentReconcileLogRepository;
import com.groove.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentReconcileServiceTest {

	private static final Long ORDER_ID = 500L;
	private static final Long PAYMENT_ID = 900L;
	private static final BigDecimal AMOUNT = BigDecimal.ZERO;
	private static final String PAYMENT_KEY = "tviva-recon";

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private PaymentConfirmWriter writer;

	@Mock
	private PaymentCancelWriter cancelWriter;

	@Mock
	private PaymentReconcileLogRepository logRepository;

	@Mock
	private AlertNotifier alertNotifier;

	private PaymentReconcileService service;

	private Clock clock;
	private LocalDateTime now;
	private Member member;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-13T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		PaymentReconcileProperties properties = new PaymentReconcileProperties(Duration.ofSeconds(60),
				Duration.ofMinutes(2), 50, 10);
		service = new PaymentReconcileService(paymentRepository, orderRepository, writer, cancelWriter, logRepository,
				properties, clock, alertNotifier);
		member = MemberFixture.withId(MemberFixture.create(), 1L);
	}

	@Nested
	@DisplayName("apply()")
	class Apply {

		@Test
		@DisplayName("결제가 이미 해소됐으면 아무것도 하지 않는다")
		void doesNothingWhenAlreadyResolved() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			Payment payment = donePayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			PaymentReconcileOutcome outcome = service.apply(candidate(), lookupOf(PaymentLookupStatus.DONE, AMOUNT));

			// then
			assertThat(outcome.needsCompensation()).isFalse();
			verify(writer, never()).approve(any(), any(), any(), any());
			verify(logRepository, never()).save(any());
		}

		@Test
		@DisplayName("토스가 DONE 이고 주문이 PENDING 이면 승인을 반영하고 APPROVED 로 남긴다")
		void appliesApproveDecision() {
			// given
			Order order = orderWithStatus(OrderStatus.PENDING);
			Payment payment = readyPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			PaymentReconcileOutcome outcome = service.apply(candidate(), lookupOf(PaymentLookupStatus.DONE, AMOUNT));

			// then
			assertThat(outcome.needsCompensation()).isFalse();
			verify(writer).approve(eq(ORDER_ID), eq(PAYMENT_ID), eq(PAYMENT_KEY), any());
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.APPROVED);
		}

		@Test
		@DisplayName("토스 조회가 없음이면 결제를 FAILED 로 남긴다")
		void appliesFailDecision() {
			// given
			Order order = orderWithStatus(OrderStatus.PENDING);
			Payment payment = readyPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			service.apply(candidate(), lookupOf(PaymentLookupStatus.NOT_FOUND, AMOUNT));

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.FAILED);
		}

		@Test
		@DisplayName("만료로 취소된 주문에 토스 DONE 이면 상태를 바꾸지 않고 보상이 필요함을 반환한다")
		void returnsNeedsCompensationForCompensateDecision() {
			// given
			Order order = orderWithStatus(OrderStatus.CANCELED);
			Payment payment = readyPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			LocalDateTime approvedAt = now.minusMinutes(5);
			PaymentLookupResult lookup = new PaymentLookupResult(PaymentLookupStatus.DONE, PAYMENT_KEY, "카드", AMOUNT,
					approvedAt, null);

			// when
			PaymentReconcileOutcome outcome = service.apply(candidate(), lookup);

			// then
			assertThat(outcome.needsCompensation()).isTrue();
			assertThat(outcome.paymentKey()).isEqualTo(PAYMENT_KEY);
			assertThat(outcome.approvedAt()).isEqualTo(approvedAt);
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
			verify(logRepository, never()).save(any());
		}

		@Test
		@DisplayName("토스가 이미 취소된 결제면 SYNC_CANCELED 로 결제를 CANCELED 로 남긴다")
		void appliesSyncCanceledDecision() {
			// given
			Order order = orderWithStatus(OrderStatus.PENDING);
			Payment payment = readyPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			service.apply(candidate(), lookupOf(PaymentLookupStatus.CANCELED, AMOUNT));

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.CANCELED);
		}

		@Test
		@DisplayName("토스가 아직 진행 중이면 재시도 횟수만 올리고 SKIPPED 로 남긴다")
		void appliesSkipDecision() {
			// given
			Order order = orderWithStatus(OrderStatus.PENDING);
			Payment payment = readyPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			service.apply(candidate(), lookupOf(PaymentLookupStatus.IN_PROGRESS, AMOUNT));

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
			assertThat(payment.getReconcileAttempts()).isEqualTo(1);
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.SKIPPED);
		}

		@Test
		@DisplayName("부분 취소처럼 자동 처리 금지 대상이면 상태는 유지하고 MANUAL_REVIEW 로 남긴다")
		void appliesManualReviewDecision() {
			// given
			Order order = orderWithStatus(OrderStatus.PENDING);
			Payment payment = readyPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			service.apply(candidate(), lookupOf(PaymentLookupStatus.PARTIAL_CANCELED, AMOUNT));

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.MANUAL_REVIEW);
			ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
			verify(alertNotifier).notify(alertCaptor.capture());
			assertThat(alertCaptor.getValue().severity()).isEqualTo(AlertSeverity.CRITICAL);
			assertThat(alertCaptor.getValue().key()).isEqualTo("payment.reconcile-manual-review");
		}

		@Test
		@DisplayName("CANCEL_REQUESTED 와 토스 CANCELED 면 T2 복구를 실행하고 CANCELED 로 기록한다")
		void completesCancelWhenTossCanceled() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			Payment payment = cancelRequestedPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(cancelWriter.completeCancel(eq(ORDER_ID), eq(PAYMENT_ID), eq(now)))
					.willReturn(Optional.empty());

			// when
			PaymentReconcileOutcome outcome = service.apply(candidate(), lookupOf(PaymentStatus.CANCEL_REQUESTED,
					PaymentLookupStatus.CANCELED, AMOUNT));

			// then
			assertThat(outcome.needsCancelRetry()).isFalse();
			verify(cancelWriter).completeCancel(ORDER_ID, PAYMENT_ID, now);
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.CANCELED);
		}

		@Test
		@DisplayName("CANCEL_REQUESTED 와 토스 DONE 이면 취소 재시도를 반환한다")
		void returnsCancelRetryWhenTossDone() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			Payment payment = cancelRequestedPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			PaymentReconcileOutcome outcome = service.apply(candidate(), lookupOf(PaymentStatus.CANCEL_REQUESTED,
					PaymentLookupStatus.DONE, AMOUNT));

			// then
			assertThat(outcome.needsCancelRetry()).isTrue();
			assertThat(outcome.paymentKey()).isEqualTo(PAYMENT_KEY);
			verify(logRepository, never()).save(any());
		}
	}

	@Nested
	@DisplayName("apply() 재시도 상한")
	class ApplyRetryLimit {

		@Test
		@DisplayName("상한에 닿기 전이면 SKIPPED 로만 남긴다")
		void skipsBeforeReachingMaxAttempts() {
			// given
			Order order = orderWithStatus(OrderStatus.PENDING);
			Payment payment = readyPayment(order);
			ReflectionTestUtils.setField(payment, "reconcileAttempts", 8);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			service.apply(candidate(), lookupOf(PaymentLookupStatus.IN_PROGRESS, AMOUNT));

			// then
			assertThat(payment.getReconcileAttempts()).isEqualTo(9);
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.SKIPPED);
		}

		@Test
		@DisplayName("상한에 닿았고 DONE 관측 이력이 있으면 상태를 유지하고 MANUAL_REVIEW 로 남긴다")
		void marksManualReviewWhenMaxAttemptsReachedWithDoneHistory() {
			// given
			Order order = orderWithStatus(OrderStatus.PENDING);
			Payment payment = readyPayment(order);
			ReflectionTestUtils.setField(payment, "reconcileAttempts", 9);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(logRepository.existsByPaymentIdAndTossStatus(PAYMENT_ID, "DONE")).willReturn(true);

			// when
			service.apply(candidate(), lookupOf(PaymentLookupStatus.IN_PROGRESS, AMOUNT));

			// then
			assertThat(payment.getReconcileAttempts()).isEqualTo(10);
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.MANUAL_REVIEW);
		}

		@Test
		@DisplayName("상한에 닿았고 관측 이력이 없으면 결제를 FAILED 로 확정한다")
		void failsWhenMaxAttemptsReachedWithoutHistory() {
			// given
			Order order = orderWithStatus(OrderStatus.PENDING);
			Payment payment = readyPayment(order);
			ReflectionTestUtils.setField(payment, "reconcileAttempts", 9);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(logRepository.existsByPaymentIdAndTossStatus(eq(PAYMENT_ID), any())).willReturn(false);

			// when
			service.apply(candidate(), lookupOf(PaymentLookupStatus.IN_PROGRESS, AMOUNT));

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.FAILED);
		}
	}

	@Nested
	@DisplayName("recordCompensation()")
	class RecordCompensation {

		@Test
		@DisplayName("보상 취소가 성공했으면 CANCELED 로 남긴다")
		void logsCanceledWhenCompensationSucceeded() {
			// given
			Order order = orderWithStatus(OrderStatus.CANCELED);
			Payment payment = compensatedPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			service.recordCompensation(candidate(), CompensationResult.canceled(now));

			// then
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.CANCELED);
			verify(paymentRepository, never()).save(any());
		}

		@Test
		@DisplayName("보상 취소가 실패했으면 miss 처리 후 SKIPPED 로 남긴다")
		void recordsMissWhenCompensationFailed() {
			// given
			Order order = orderWithStatus(OrderStatus.CANCELED);
			Payment payment = readyPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			service.recordCompensation(candidate(), CompensationResult.notCanceled("TOSS 통신 실패"));

			// then
			assertThat(payment.getReconcileAttempts()).isEqualTo(1);
			PaymentReconcileLog log = capturedLog();
			assertThat(log.getAction()).isEqualTo(PaymentReconcileAction.SKIPPED);
			assertThat(log.getDetail()).isEqualTo("TOSS 통신 실패");
		}
	}

	@Nested
	@DisplayName("recordFailure()")
	class RecordFailure {

		@Test
		@DisplayName("결제가 unresolved 면 miss 처리 후 SKIPPED 로 남긴다")
		void recordsMissWhenPaymentUnresolved() {
			// given
			Order order = orderWithStatus(OrderStatus.PENDING);
			Payment payment = readyPayment(order);
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			service.recordFailure(candidate(), "조회 실패");

			// then
			assertThat(payment.getReconcileAttempts()).isEqualTo(1);
			PaymentReconcileLog log = capturedLog();
			assertThat(log.getAction()).isEqualTo(PaymentReconcileAction.SKIPPED);
			assertThat(log.getTossStatus()).isEqualTo("LOOKUP_ERROR");
		}

		@Test
		@DisplayName("CANCEL_REQUESTED 가 결과 불명이면 상태를 유지하고 SKIPPED 로 남긴다")
		void recordsMissWhenCancelRequestedResultUnknown() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			Payment payment = cancelRequestedPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			service.recordCancelRetry(candidate(), null, new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN));

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.SKIPPED);
		}

		@Test
		@DisplayName("취소 재시도가 성공하면 T2 완료와 CANCELED 로그를 남긴다")
		void completesCancelRetryWhenTossCancelSucceeds() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			Payment payment = cancelRequestedPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(cancelWriter.completeCancel(eq(ORDER_ID), eq(PAYMENT_ID), eq(now)))
					.willReturn(Optional.empty());
			PaymentCancelResult result = new PaymentCancelResult(PAYMENT_KEY, "CANCELED", now);

			// when
			service.recordCancelRetry(candidate(), result, null);

			// then
			verify(cancelWriter).completeCancel(ORDER_ID, PAYMENT_ID, now);
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.CANCELED);
		}

		@Test
		@DisplayName("취소 재시도가 거절되면 DONE 복귀와 MANUAL_REVIEW 로그를 남긴다")
		void revertsCancelRetryWhenTossRejects() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			Payment payment = cancelRequestedPayment(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			BusinessException failure = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);

			// when
			service.recordCancelRetry(candidate(), null, failure);

			// then
			verify(cancelWriter).revertCancelRequest(ORDER_ID, PAYMENT_ID);
			PaymentReconcileLog log = capturedLog();
			assertThat(log.getAction()).isEqualTo(PaymentReconcileAction.MANUAL_REVIEW);
			assertThat(log.getDetail()).isEqualTo("토스가 취소를 거절");
		}

		@Test
		@DisplayName("취소 재시도 결과 불명이 상한에 도달하면 상태를 유지하고 MANUAL_REVIEW 한다")
		void keepsCancelRequestedAtRetryLimitWhenResultUnknown() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			Payment payment = cancelRequestedPayment(order);
			ReflectionTestUtils.setField(payment, "reconcileAttempts", 9);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			service.recordCancelRetry(candidate(), null, new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN));

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
			assertThat(payment.getReconcileAttempts()).isEqualTo(10);
			assertThat(capturedLog().getAction()).isEqualTo(PaymentReconcileAction.MANUAL_REVIEW);
		}

		@Test
		@DisplayName("결제가 이미 해소됐으면 아무것도 하지 않는다")
		void doesNothingWhenPaymentAlreadyResolved() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			Payment payment = donePayment(order);
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			service.recordFailure(candidate(), "조회 실패");

			// then
			verify(logRepository, never()).save(any());
		}

		@Test
		@DisplayName("결제를 찾을 수 없으면 아무것도 하지 않는다")
		void doesNothingWhenPaymentNotFound() {
			// given
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());

			// when
			service.recordFailure(candidate(), "조회 실패");

			// then
			verify(logRepository, never()).save(any());
		}
	}

	private PaymentReconcileLog capturedLog() {
		ArgumentCaptor<PaymentReconcileLog> captor = ArgumentCaptor.forClass(PaymentReconcileLog.class);
		verify(logRepository, times(1)).save(captor.capture());
		return captor.getValue();
	}

	private PaymentReconcileCandidate candidate() {
		return new PaymentReconcileCandidate(PAYMENT_ID, ORDER_ID, OrderFixture.create(member).getOrderNumber());
	}

	private PaymentLookupResult lookupOf(PaymentLookupStatus status, BigDecimal amount) {
		return new PaymentLookupResult(status, PAYMENT_KEY, "카드", amount, now.minusMinutes(5), now);
	}

	private PaymentLookupResult lookupOf(PaymentStatus paymentStatus, PaymentLookupStatus lookupStatus,
			BigDecimal amount) {
		return new PaymentLookupResult(lookupStatus, PAYMENT_KEY, "카드", amount, now.minusMinutes(5), now);
	}

	private Order orderWithStatus(OrderStatus status) {
		Order order = OrderFixture.withId(OrderFixture.create(member), ORDER_ID);
		ReflectionTestUtils.setField(order, "status", status);
		return order;
	}

	private Payment readyPayment(Order order) {
		Payment payment = Payment.ready(order);
		ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
		return payment;
	}

	private Payment donePayment(Order order) {
		Payment payment = readyPayment(order);
		payment.approve(PAYMENT_KEY, "카드", now.minusMinutes(10));
		return payment;
	}

	private Payment cancelRequestedPayment(Order order) {
		Payment payment = donePayment(order);
		payment.requestCancel();
		return payment;
	}

	private Payment compensatedPayment(Order order) {
		Payment payment = readyPayment(order);
		payment.compensate(PAYMENT_KEY, now.minusMinutes(10), now, "대사: 토스에서 이미 취소됨");
		return payment;
	}
}
