package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.service.OrderClaimFinalizeService;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.client.dto.RefundAccountInfo;
import com.groove.payment.config.PaymentReconcileProperties;
import com.groove.payment.dto.PaymentCancelRetryCandidate;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentReconcileAction;
import com.groove.payment.entity.PaymentReconcileLog;
import com.groove.payment.repository.PaymentReconcileLogRepository;
import com.groove.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentCancelRetrierTest {

	private static final Long PAYMENT_ID = 40L;
	private static final Long PAYMENT_CANCEL_ID = 400L;
	private static final String PAYMENT_KEY = "tviva-retry-key";
	private static final String TOSS_ORDER_ID = "20260929-RETRY001";
	private static final String IDEMPOTENCY_KEY = "cancel-" + PAYMENT_KEY + "-1";
	private static final BigDecimal CANCEL_AMOUNT = new BigDecimal("10000");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 12, 0);

	@Mock
	private PaymentRefundWriter refundWriter;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentReconcileLogRepository reconcileLogRepository;

	@Mock
	private PaymentClient paymentClient;

	@Mock
	private AlertNotifier alertNotifier;

	@Mock
	private OrderClaimFinalizeService orderClaimFinalizeService;

	@Mock
	private OrderClaimRepository orderClaimRepository;

	private PaymentCancelRetrier retrier;
	private Clock clock;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
		PaymentReconcileProperties properties = new PaymentReconcileProperties(Duration.ofSeconds(60),
				Duration.ofMinutes(2), 50, 10, Duration.ofMinutes(1));
		retrier = new PaymentCancelRetrier(refundWriter, paymentRepository, reconcileLogRepository, paymentClient,
				properties, clock, alertNotifier, orderClaimFinalizeService,
				orderClaimRepository);
	}

	@Nested
	@DisplayName("retry()")
	class Retry {

		@Test
		@DisplayName("재시도 유예 기간이 지났고 상한 전이면 같은 idempotencyKey 로 다시 취소를 부른다")
		void retriesWithSameIdempotencyKeyWithinVerifyWindow() {
			// given: refundRetryGrace(1분)는 지났고 refundVerifyAfter(1분 * 10 = 10분) 안쪽인 5분 전 요청
			PaymentCancelRetryCandidate candidate = candidate(NOW.minusMinutes(5));
			LocalDateTime canceledAt = NOW.minusMinutes(1);
			given(paymentClient.cancel(PaymentCancelCommand.of(PAYMENT_KEY, "부분 반품", CANCEL_AMOUNT, IDEMPOTENCY_KEY,
					null))).willReturn(new PaymentCancelResult(PAYMENT_KEY, "PARTIAL_CANCELED", canceledAt,
							"txn-retry-1", BigDecimal.ZERO));

			// when
			retrier.retry(candidate);

			// then
			verify(refundWriter).completeRefund(PAYMENT_ID, PAYMENT_CANCEL_ID, CANCEL_AMOUNT, "txn-retry-1",
					canceledAt);
			verifyNoInteractions(paymentRepository, reconcileLogRepository, alertNotifier);
		}

		@Test
		@DisplayName("클레임 환불이면 클레임에 저장된 환불계좌를 취소 요청에 싣는다")
		void sendsClaimRefundAccountWhenClaimExists() {
			// given
			Long claimId = 700L;
			RefundAccountInfo account = new RefundAccountInfo("088", "110123456789", "홍길동");
			OrderClaim claim = mock(OrderClaim.class);
			given(claim.getRefundAccount()).willReturn(account);
			given(orderClaimRepository.findById(claimId)).willReturn(Optional.of(claim));
			given(paymentClient.cancel(any(PaymentCancelCommand.class))).willReturn(new PaymentCancelResult(
					PAYMENT_KEY, "PARTIAL_CANCELED", NOW.minusMinutes(1), "txn-retry-1", BigDecimal.ZERO));

			// when
			retrier.retry(candidateWithClaim(NOW.minusMinutes(5), claimId));

			// then
			ArgumentCaptor<PaymentCancelCommand> captor = ArgumentCaptor.forClass(PaymentCancelCommand.class);
			verify(paymentClient).cancel(captor.capture());
			assertThat(captor.getValue().refundReceiveAccount()).isEqualTo(account);
			assertThat(captor.getValue().idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
		}

		@Test
		@DisplayName("클레임 행이 없으면 환불계좌 없이 취소를 부른다")
		void sendsNullAccountWhenClaimMissing() {
			// given
			Long claimId = 700L;
			given(orderClaimRepository.findById(claimId)).willReturn(Optional.empty());
			given(paymentClient.cancel(any(PaymentCancelCommand.class))).willReturn(new PaymentCancelResult(
					PAYMENT_KEY, "PARTIAL_CANCELED", NOW.minusMinutes(1), "txn-retry-1", BigDecimal.ZERO));

			// when
			retrier.retry(candidateWithClaim(NOW.minusMinutes(5), claimId));

			// then
			ArgumentCaptor<PaymentCancelCommand> captor = ArgumentCaptor.forClass(PaymentCancelCommand.class);
			verify(paymentClient).cancel(captor.capture());
			assertThat(captor.getValue().refundReceiveAccount()).isNull();
		}

		@Test
		@DisplayName("클레임과 무관한 취소 건이면 클레임을 조회하지 않고 환불계좌 없이 부른다")
		void sendsNullAccountWithoutClaimLookupWhenNoClaimId() {
			// given
			given(paymentClient.cancel(any(PaymentCancelCommand.class))).willReturn(new PaymentCancelResult(
					PAYMENT_KEY, "PARTIAL_CANCELED", NOW.minusMinutes(1), "txn-retry-1", BigDecimal.ZERO));

			// when
			retrier.retry(candidate(NOW.minusMinutes(5)));

			// then
			ArgumentCaptor<PaymentCancelCommand> captor = ArgumentCaptor.forClass(PaymentCancelCommand.class);
			verify(paymentClient).cancel(captor.capture());
			assertThat(captor.getValue().refundReceiveAccount()).isNull();
			verifyNoInteractions(orderClaimRepository);
		}

		@Test
		@DisplayName("클레임 승인으로 시작된 취소가 재시도로 완료되면 클레임 마무리는 completeRefund 에 맡기고 직접 호출하지 않는다")
		void leavesClaimFinalizationToCompleteRefund() {
			// given
			Long claimId = 700L;
			PaymentCancelRetryCandidate candidate = candidateWithClaim(NOW.minusMinutes(5), claimId);
			LocalDateTime canceledAt = NOW.minusMinutes(1);
			given(paymentClient.cancel(PaymentCancelCommand.of(PAYMENT_KEY, "부분 반품", CANCEL_AMOUNT, IDEMPOTENCY_KEY,
					null))).willReturn(new PaymentCancelResult(PAYMENT_KEY, "PARTIAL_CANCELED", canceledAt,
							"txn-retry-1", BigDecimal.ZERO));

			// when
			retrier.retry(candidate);

			// then
			verify(refundWriter).completeRefund(PAYMENT_ID, PAYMENT_CANCEL_ID, CANCEL_AMOUNT, "txn-retry-1",
					canceledAt);
			verifyNoInteractions(orderClaimFinalizeService);
		}

		@Test
		@DisplayName("클레임 승인으로 시작된 취소를 토스가 거절하면 클레임을 되돌린다")
		void revertsClaimWhenTossRejectsExplicitly() {
			// given
			Long claimId = 700L;
			PaymentCancelRetryCandidate candidate = candidateWithClaim(NOW.minusMinutes(5), claimId);
			given(paymentClient.cancel(any(PaymentCancelCommand.class)))
					.willThrow(new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED));

			// when
			retrier.retry(candidate);

			// then
			verify(orderClaimFinalizeService).applyRefundFailed(claimId);
		}

		@Test
		@DisplayName("토스가 명시적으로 거절하면 취소 건을 FAILED 로 기록한다")
		void failsWhenTossRejectsExplicitly() {
			// given
			PaymentCancelRetryCandidate candidate = candidate(NOW.minusMinutes(5));
			given(paymentClient.cancel(any(PaymentCancelCommand.class)))
					.willThrow(new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED));

			// when
			retrier.retry(candidate);

			// then
			verify(refundWriter).failRefund(PAYMENT_CANCEL_ID);
			verify(refundWriter, never()).completeRefund(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("결과가 불명이면 아무 것도 반영하지 않고 다음 주기로 넘긴다")
		void doesNothingWhenResultIsUnknown() {
			// given
			PaymentCancelRetryCandidate candidate = candidate(NOW.minusMinutes(5));
			given(paymentClient.cancel(any(PaymentCancelCommand.class)))
					.willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN));

			// when
			retrier.retry(candidate);

			// then
			verifyNoInteractions(refundWriter);
		}

		@Test
		@DisplayName("통신 실패로 결과를 알 수 없으면 아무 것도 반영하지 않는다")
		void doesNothingWhenCommunicationFails() {
			// given
			PaymentCancelRetryCandidate candidate = candidate(NOW.minusMinutes(5));
			given(paymentClient.cancel(any(PaymentCancelCommand.class)))
					.willThrow(new RuntimeException("Read timed out"));

			// when
			retrier.retry(candidate);

			// then
			verifyNoInteractions(refundWriter);
		}

		@Test
		@DisplayName("재시도 상한을 넘기면 재호출 대신 조회로 확인하고, 우리 취소가 반영된 잔액이면 완료 처리한다")
		void completesFromLookupWhenPastVerifyThresholdAndBalanceMatches() {
			// given: refundVerifyAfter(10분)를 넘긴 11분 전 요청
			PaymentCancelRetryCandidate candidate = candidate(NOW.minusMinutes(11));
			Payment payment = paymentWith(new BigDecimal("30000"), new BigDecimal("5000"));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			LocalDateTime canceledAt = NOW.minusMinutes(1);
			// 남은 금액(30000 - 5000 - 10000 = 15000)과 토스 balanceAmount 가 일치
			given(paymentClient.lookup(TOSS_ORDER_ID)).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.PARTIAL_CANCELED, PAYMENT_KEY, "카드", null, null, canceledAt,
					new BigDecimal("15000"), "txn-verify-1"));

			// when
			retrier.retry(candidate);

			// then
			verify(refundWriter).completeRefund(PAYMENT_ID, PAYMENT_CANCEL_ID, CANCEL_AMOUNT, "txn-verify-1",
					canceledAt);
			verify(paymentClient, never()).cancel(any(PaymentCancelCommand.class));
			verify(reconcileLogRepository, never()).save(any());
			verifyNoInteractions(alertNotifier);
		}

		@Test
		@DisplayName("재시도 상한을 넘기고 우리 취소가 반영되지 않은 잔액이면 가드가 풀리도록 FAILED 로 닫는다")
		void failsWhenPastVerifyThresholdAndCancelWasNotApplied() {
			// given: 재호출을 이미 멈췄으니(age >= refundVerifyAfter) 이 판정 뒤로 늦게 적용될 위험이 없다.
			PaymentCancelRetryCandidate candidate = candidate(NOW.minusMinutes(11));
			Payment payment = paymentWith(new BigDecimal("30000"), new BigDecimal("5000"));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			// 취소 전 남은 금액(30000 - 5000 = 25000)과 토스 balanceAmount 가 그대로 일치 - 이번 취소는
			// 반영되지 않았다.
			given(paymentClient.lookup(TOSS_ORDER_ID)).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, PAYMENT_KEY, "카드", null, null, null,
					new BigDecimal("25000"), null));

			// when
			retrier.retry(candidate);

			// then
			verify(refundWriter).failRefund(PAYMENT_CANCEL_ID);
			verify(refundWriter, never()).completeRefund(any(), any(), any(), any(), any());
			verify(reconcileLogRepository, never()).save(any());
			verifyNoInteractions(alertNotifier);
		}

		@Test
		@DisplayName("재시도 상한을 넘기고 잔액이 반영됨·미반영 어느 쪽과도 안 맞으면 행은 REQUESTED 로 둔 채 MANUAL_REVIEW 로만 남긴다")
		void marksManualReviewWhenPastVerifyThresholdAndBalanceMismatches() {
			// given
			PaymentCancelRetryCandidate candidate = candidate(NOW.minusMinutes(11));
			Payment payment = paymentWith(new BigDecimal("30000"), new BigDecimal("5000"));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			// 취소 전(25000)도, 취소 반영 후(15000)도 아닌 20000 - 다른 경로 취소가 섞인 드리프트라 판단 불가.
			given(paymentClient.lookup(TOSS_ORDER_ID)).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.PARTIAL_CANCELED, PAYMENT_KEY, "카드", null, null, null,
					new BigDecimal("20000"), "txn-verify-2"));

			// when
			retrier.retry(candidate);

			// then
			verify(refundWriter, never()).completeRefund(any(), any(), any(), any(), any());
			verify(refundWriter, never()).failRefund(any());
			verify(alertNotifier).notify(any(Alert.class));
			ArgumentCaptor<PaymentReconcileLog> captor = ArgumentCaptor.forClass(PaymentReconcileLog.class);
			verify(reconcileLogRepository).save(captor.capture());
			assertThat(captor.getValue().getAction()).isEqualTo(PaymentReconcileAction.MANUAL_REVIEW);
			assertThat(captor.getValue().getTossStatus()).isEqualTo(PaymentCancelRetrier.MANUAL_REVIEW_MARKER);
		}

		@Test
		@DisplayName("재시도 상한을 넘기고 조회에 잔액이 없으면 판단할 수 없어 MANUAL_REVIEW 로 남긴다")
		void marksManualReviewWhenPastVerifyThresholdAndBalanceMissing() {
			// given
			PaymentCancelRetryCandidate candidate = candidate(NOW.minusMinutes(11));
			Payment payment = paymentWith(new BigDecimal("30000"), new BigDecimal("5000"));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(paymentClient.lookup(TOSS_ORDER_ID)).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.PARTIAL_CANCELED, PAYMENT_KEY, "카드", null, null, null));

			// when
			retrier.retry(candidate);

			// then
			verify(refundWriter, never()).completeRefund(any(), any(), any(), any(), any());
			verify(refundWriter, never()).failRefund(any());
			verify(alertNotifier).notify(any(Alert.class));
		}

		@Test
		@DisplayName("이미 수동 확인 표식이 남아 있으면 더 조회하지 않는다")
		void skipsWhenAlreadyMarkedManualReview() {
			// given
			PaymentCancelRetryCandidate candidate = candidate(NOW.minusMinutes(11));
			given(reconcileLogRepository.existsByPaymentIdAndTossStatus(PAYMENT_ID,
					PaymentCancelRetrier.MANUAL_REVIEW_MARKER)).willReturn(true);

			// when
			retrier.retry(candidate);

			// then
			verifyNoInteractions(paymentClient, paymentRepository, refundWriter, alertNotifier);
			verify(reconcileLogRepository, never()).save(any());
		}

		@Test
		@DisplayName("15일 멱등키 유효기간을 넘기면 설정과 무관하게 재호출 대신 조회만 한다")
		void neverRetriesCallPastIdempotencyKeyHardStop() {
			// given: refundRetryGrace 를 크게 잡아 refundVerifyAfter 가 14일보다 뒤에 오도록 해도, 실제
			// age(15일)가 하드 스톱을 넘기면 재호출은 절대 하지 않는다.
			PaymentReconcileProperties longGraceProperties = new PaymentReconcileProperties(Duration.ofSeconds(60),
					Duration.ofMinutes(2), 50, 1, Duration.ofDays(30));
			PaymentCancelRetrier longGraceRetrier = new PaymentCancelRetrier(refundWriter, paymentRepository,
					reconcileLogRepository, paymentClient, longGraceProperties, clock, alertNotifier,
					orderClaimFinalizeService, orderClaimRepository);
			PaymentCancelRetryCandidate candidate = candidate(NOW.minusDays(15));
			Payment payment = paymentWith(new BigDecimal("30000"), BigDecimal.ZERO);
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(paymentClient.lookup(TOSS_ORDER_ID)).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.PARTIAL_CANCELED, PAYMENT_KEY, "카드", null, null, null,
					new BigDecimal("20000"), "txn-hardstop"));

			// when
			longGraceRetrier.retry(candidate);

			// then
			verify(paymentClient, never()).cancel(any(PaymentCancelCommand.class));
			verify(paymentClient).lookup(TOSS_ORDER_ID);
		}

		@Test
		@DisplayName("재시도 성공 후 반영이 실패하면 다음 주기로 넘기고 클레임은 건드리지 않는다")
		void keepsGoingWhenCompletionAfterRetryFails() {
			// given
			PaymentCancelRetryCandidate candidate = candidateWithClaim(NOW.minusMinutes(5), 700L);
			LocalDateTime canceledAt = NOW.minusMinutes(1);
			given(paymentClient.cancel(any(PaymentCancelCommand.class))).willReturn(
					new PaymentCancelResult(PAYMENT_KEY, "PARTIAL_CANCELED", canceledAt, "txn-retry-1",
							BigDecimal.ZERO));
			willThrow(new IllegalStateException("반영 실패")).given(refundWriter).completeRefund(PAYMENT_ID,
					PAYMENT_CANCEL_ID, CANCEL_AMOUNT, "txn-retry-1", canceledAt);

			// when
			retrier.retry(candidate);

			// then
			verifyNoInteractions(orderClaimFinalizeService);
		}

		@Test
		@DisplayName("거절 기록 자체가 실패해도 예외를 전파하지 않는다")
		void doesNotPropagateWhenFailRefundRecordingFails() {
			// given
			PaymentCancelRetryCandidate candidate = candidateWithClaim(NOW.minusMinutes(5), 700L);
			given(paymentClient.cancel(any(PaymentCancelCommand.class)))
					.willThrow(new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED));
			willThrow(new IllegalStateException("기록 실패")).given(refundWriter).failRefund(PAYMENT_CANCEL_ID);

			// when
			retrier.retry(candidate);

			// then
			verifyNoInteractions(orderClaimFinalizeService);
		}

		@Test
		@DisplayName("확인 조회 자체가 실패하면 아무 것도 반영하지 않는다")
		void doesNothingWhenLookupCommunicationFails() {
			// given
			PaymentCancelRetryCandidate candidate = candidate(NOW.minusMinutes(11));
			given(paymentClient.lookup(TOSS_ORDER_ID)).willThrow(new RuntimeException("Read timed out"));

			// when
			retrier.retry(candidate);

			// then
			verifyNoInteractions(refundWriter, alertNotifier);
			verify(reconcileLogRepository, never()).save(any());
		}

		@Test
		@DisplayName("미반영 확정 기록이 실패해도 예외를 전파하지 않는다")
		void doesNotPropagateWhenFailNotAppliedRecordingFails() {
			// given
			PaymentCancelRetryCandidate candidate = candidateWithClaim(NOW.minusMinutes(11), 700L);
			Payment payment = paymentWith(new BigDecimal("30000"), new BigDecimal("5000"));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(paymentClient.lookup(TOSS_ORDER_ID)).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, PAYMENT_KEY, "카드", null, null, null, new BigDecimal("25000"), null));
			willThrow(new IllegalStateException("기록 실패")).given(refundWriter).failRefund(PAYMENT_CANCEL_ID);

			// when
			retrier.retry(candidate);

			// then
			verifyNoInteractions(orderClaimFinalizeService);
		}

		@Test
		@DisplayName("확인 조회로 완료 반영이 실패해도 예외를 전파하지 않는다")
		void doesNotPropagateWhenCompleteFromLookupFails() {
			// given
			PaymentCancelRetryCandidate candidate = candidateWithClaim(NOW.minusMinutes(11), 700L);
			Payment payment = paymentWith(new BigDecimal("30000"), new BigDecimal("5000"));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			LocalDateTime canceledAt = NOW.minusMinutes(1);
			given(paymentClient.lookup(TOSS_ORDER_ID)).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.PARTIAL_CANCELED, PAYMENT_KEY, "카드", null, null, canceledAt,
					new BigDecimal("15000"), "txn-verify-1"));
			willThrow(new IllegalStateException("반영 실패")).given(refundWriter).completeRefund(PAYMENT_ID,
					PAYMENT_CANCEL_ID, CANCEL_AMOUNT, "txn-verify-1", canceledAt);

			// when
			retrier.retry(candidate);

			// then
			verifyNoInteractions(orderClaimFinalizeService);
		}

		@Test
		@DisplayName("클레임 거절 되돌리기 자체가 실패해도 예외를 전파하지 않는다")
		void doesNotPropagateWhenFinalizeFailedFails() {
			// given
			Long claimId = 700L;
			PaymentCancelRetryCandidate candidate = candidateWithClaim(NOW.minusMinutes(5), claimId);
			given(paymentClient.cancel(any(PaymentCancelCommand.class)))
					.willThrow(new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED));
			willThrow(new IllegalStateException("되돌리기 실패")).given(orderClaimFinalizeService)
					.applyRefundFailed(claimId);

			// when & then
			org.assertj.core.api.Assertions.assertThatCode(() -> retrier.retry(candidate)).doesNotThrowAnyException();
		}
	}

	private PaymentCancelRetryCandidate candidate(LocalDateTime requestedAt) {
		return new PaymentCancelRetryCandidate(PAYMENT_CANCEL_ID, PAYMENT_ID, PAYMENT_KEY, TOSS_ORDER_ID,
				CANCEL_AMOUNT, IDEMPOTENCY_KEY, "부분 반품", requestedAt, null);
	}

	private PaymentCancelRetryCandidate candidateWithClaim(LocalDateTime requestedAt, Long orderClaimId) {
		return new PaymentCancelRetryCandidate(PAYMENT_CANCEL_ID, PAYMENT_ID, PAYMENT_KEY, TOSS_ORDER_ID,
				CANCEL_AMOUNT, IDEMPOTENCY_KEY, "부분 반품", requestedAt, orderClaimId);
	}

	private Payment paymentWith(BigDecimal amount, BigDecimal canceledAmount) {
		Order order = OrderFixture.withId(OrderFixture.create(MemberFixture.create()), 1L);
		Payment payment = Payment.ready(order);
		ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
		ReflectionTestUtils.setField(payment, "amount", amount);
		ReflectionTestUtils.setField(payment, "canceledAmount", canceledAmount);
		return payment;
	}
}
