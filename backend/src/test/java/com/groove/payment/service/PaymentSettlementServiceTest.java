package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.order.entity.Order;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.client.dto.PaymentTransaction;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentSettlementServiceTest {

	private static final Long ORDER_ID = 500L;
	private static final LocalDateTime FROM = LocalDateTime.of(2026, 9, 21, 0, 0, 0);
	private static final LocalDateTime TO = LocalDateTime.of(2026, 9, 22, 0, 0, 0);

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentClient paymentClient;

	@Mock
	private PaymentLateResultApplier lateResultApplier;

	@Mock
	private AlertNotifier alertNotifier;

	private PaymentSettlementService service;

	@BeforeEach
	void setUp() {
		service = new PaymentSettlementService(paymentRepository, paymentClient, lateResultApplier, alertNotifier);
		given(paymentRepository.findByApprovedAtGreaterThanEqualAndApprovedAtLessThan(FROM, TO))
				.willReturn(List.of());
	}

	@Nested
	@DisplayName("reconcile()")
	class Reconcile {

		@Test
		@DisplayName("DB 와 토스 상태가 일치하면 조회 없이 matched 로 센다")
		void countsMatchedWithoutLookupWhenStatusesAgree() {
			// given
			Payment payment = payment(1L, "20260921-MATCH0001", PaymentStatus.DONE);
			given(paymentRepository.findByTossOrderId("20260921-MATCH0001")).willReturn(Optional.of(payment));
			PaymentTransaction transaction = transaction("txn-1", "20260921-MATCH0001", "DONE",
					FROM.plusHours(1));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(transaction), FROM, TO);

			// then
			assertThat(report.matched()).isEqualTo(1);
			assertThat(report.applied()).isZero();
			assertThat(report.mismatched()).isZero();
			verifyNoInteractions(paymentClient, lateResultApplier);
		}

		@Test
		@DisplayName("DB FAILED, 토스 DONE 이면 재조회 뒤 lateResultApplier 에 위임하고 applied 로 센다")
		void appliesWhenFailedPaymentIsActuallyDone() {
			// given
			Payment payment = payment(2L, "20260921-FAIL00001", PaymentStatus.FAILED);
			given(paymentRepository.findByTossOrderId("20260921-FAIL00001")).willReturn(Optional.of(payment));
			PaymentTransaction transaction = transaction("txn-2", "20260921-FAIL00001", "DONE",
					FROM.plusHours(1));
			PaymentLookupResult lookup = lookup(PaymentLookupStatus.DONE);
			given(paymentClient.lookup("20260921-FAIL00001")).willReturn(lookup);

			// when
			PaymentSettlementReport report = service.reconcile(List.of(transaction), FROM, TO);

			// then
			assertThat(report.applied()).isEqualTo(1);
			assertThat(report.mismatched()).isZero();
			verify(lateResultApplier).apply(any(), eq(lookup), eq("settlement"));
			verifyNoInteractions(alertNotifier);
		}

		@Test
		@DisplayName("DB DONE, 토스 CANCELED 이고 재조회도 CANCELED 면 경보를 보내고 mismatched 로 센다")
		void alertsWhenLookupConfirmsMismatch() {
			// given
			Payment payment = payment(3L, "20260921-DONE00001", PaymentStatus.DONE);
			given(paymentRepository.findByTossOrderId("20260921-DONE00001")).willReturn(Optional.of(payment));
			PaymentTransaction transaction = transaction("txn-3", "20260921-DONE00001", "CANCELED",
					FROM.plusHours(1));
			given(paymentClient.lookup("20260921-DONE00001")).willReturn(lookup(PaymentLookupStatus.CANCELED));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(transaction), FROM, TO);

			// then
			assertThat(report.mismatched()).isEqualTo(1);
			assertThat(report.matched()).isZero();
			verify(alertNotifier).notify(any(Alert.class));
			verifyNoInteractions(lateResultApplier);
		}

		@Test
		@DisplayName("불일치로 재조회했는데 DONE 으로 확인되면 matched 로 세고 경보를 보내지 않는다")
		void treatsAsMatchedWhenRecheckConfirmsDone() {
			// given: 창 경계에서 거래 목록엔 취소로 찍혔지만 실제로는 DONE 인 가짜 불일치.
			Payment payment = payment(4L, "20260921-RECHECK01", PaymentStatus.DONE);
			given(paymentRepository.findByTossOrderId("20260921-RECHECK01")).willReturn(Optional.of(payment));
			PaymentTransaction transaction = transaction("txn-4", "20260921-RECHECK01", "CANCELED",
					FROM.plusHours(1));
			given(paymentClient.lookup("20260921-RECHECK01")).willReturn(lookup(PaymentLookupStatus.DONE));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(transaction), FROM, TO);

			// then
			assertThat(report.matched()).isEqualTo(1);
			assertThat(report.mismatched()).isZero();
			verifyNoInteractions(alertNotifier, lateResultApplier);
		}

		@Test
		@DisplayName("모르는 orderId 면 조회 없이 unknown 으로 세고 경보를 보내지 않는다")
		void countsUnknownWithoutAlertForUnknownOrderId() {
			// given
			given(paymentRepository.findByTossOrderId("20260921-UNKNOWN01")).willReturn(Optional.empty());
			PaymentTransaction transaction = transaction("txn-5", "20260921-UNKNOWN01", "DONE", FROM.plusHours(1));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(transaction), FROM, TO);

			// then
			assertThat(report.unknown()).isEqualTo(1);
			verifyNoInteractions(paymentClient, alertNotifier, lateResultApplier);
		}

		@Test
		@DisplayName("DB 승인 결제가 거래 목록에 없고 재조회도 NOT_FOUND 면 경보를 보낸다")
		void alertsWhenApprovedPaymentMissingFromTossList() {
			// given
			Payment payment = payment(5L, "20260921-MISSING01", PaymentStatus.DONE);
			given(paymentRepository.findByApprovedAtGreaterThanEqualAndApprovedAtLessThan(FROM, TO))
					.willReturn(List.of(payment));
			given(paymentClient.lookup("20260921-MISSING01")).willReturn(lookup(PaymentLookupStatus.NOT_FOUND));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(), FROM, TO);

			// then
			assertThat(report.mismatched()).isEqualTo(1);
			verify(alertNotifier).notify(any(Alert.class));
			verifyNoInteractions(lateResultApplier);
		}

		@Test
		@DisplayName("한 건이 예외를 던져도 나머지 후보는 계속 처리한다")
		void continuesProcessingWhenOneCandidateFails() {
			// given
			given(paymentRepository.findByTossOrderId("20260921-BOOM00001"))
					.willThrow(new RuntimeException("DB 오류"));
			Payment payment = payment(6L, "20260921-OK000001", PaymentStatus.DONE);
			given(paymentRepository.findByTossOrderId("20260921-OK000001")).willReturn(Optional.of(payment));
			PaymentTransaction failing = transaction("txn-6", "20260921-BOOM00001", "DONE", FROM.plusHours(1));
			PaymentTransaction ok = transaction("txn-7", "20260921-OK000001", "DONE", FROM.plusHours(2));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(failing, ok), FROM, TO);

			// then
			assertThat(report.failed()).isEqualTo(1);
			assertThat(report.matched()).isEqualTo(1);
		}

		@Test
		@DisplayName("같은 orderId 에 DONE·CANCELED 두 행이 있으면 CANCELED 로 판정한다")
		void picksCanceledOverDoneForSameOrder() {
			// given
			Payment payment = payment(7L, "20260921-DUP00001", PaymentStatus.CANCELED);
			given(paymentRepository.findByTossOrderId("20260921-DUP00001")).willReturn(Optional.of(payment));
			PaymentTransaction done = transaction("txn-8", "20260921-DUP00001", "DONE", FROM.plusHours(1));
			PaymentTransaction canceled = transaction("txn-9", "20260921-DUP00001", "CANCELED", FROM.plusHours(1));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(done, canceled), FROM, TO);

			// then
			assertThat(report.matched()).isEqualTo(1);
			verifyNoInteractions(paymentClient, alertNotifier, lateResultApplier);
		}

		@Test
		@DisplayName("같은 시각에 이미 취소 계열인 행이 있으면 뒤이은 DONE 행으로 덮어쓰지 않는다")
		void keepsCancelLikeTransactionOverDoneAtSameInstant() {
			// given: 취소 계열(current)이 먼저 들어오고, 같은 시각의 DONE(candidate)이 뒤이어 온다.
			Payment payment = payment(8L, "20260921-TIE00001", PaymentStatus.CANCELED);
			given(paymentRepository.findByTossOrderId("20260921-TIE00001")).willReturn(Optional.of(payment));
			PaymentTransaction canceled = transaction("txn-10", "20260921-TIE00001", "CANCELED", FROM.plusHours(1));
			PaymentTransaction done = transaction("txn-11", "20260921-TIE00001", "DONE", FROM.plusHours(1));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(canceled, done), FROM, TO);

			// then: DONE 이 이겼다면 DB CANCELED 와 불일치해 조회가 났을 것이다.
			assertThat(report.matched()).isEqualTo(1);
			verifyNoInteractions(paymentClient, alertNotifier, lateResultApplier);
		}

		@Test
		@DisplayName("transactionAt 이 없는 후보는 시각이 있는 현재 행을 밀어내지 못한다")
		void keepsTimedTransactionWhenCandidateTransactionAtIsNull() {
			// given: 시각이 있는 CANCELED(current)가 먼저, 시각이 없는 DONE(candidate)이 뒤에 온다.
			Payment payment = payment(9L, "20260921-NULLCAND1", PaymentStatus.CANCELED);
			given(paymentRepository.findByTossOrderId("20260921-NULLCAND1")).willReturn(Optional.of(payment));
			PaymentTransaction canceled = transaction("txn-12", "20260921-NULLCAND1", "CANCELED", FROM.plusHours(1));
			PaymentTransaction doneNullTime = transaction("txn-13", "20260921-NULLCAND1", "DONE", null);

			// when
			PaymentSettlementReport report = service.reconcile(List.of(canceled, doneNullTime), FROM, TO);

			// then
			assertThat(report.matched()).isEqualTo(1);
			verifyNoInteractions(paymentClient, alertNotifier, lateResultApplier);
		}

		@Test
		@DisplayName("현재 행에 시각이 없으면 시각이 있는 후보가 대신 선택된다")
		void candidateWinsWhenCurrentTransactionAtIsNull() {
			// given: 시각이 없는 DONE(current)이 먼저, 시각이 있는 CANCELED(candidate)가 뒤에 온다.
			Payment payment = payment(10L, "20260921-NULLCURR1", PaymentStatus.CANCELED);
			given(paymentRepository.findByTossOrderId("20260921-NULLCURR1")).willReturn(Optional.of(payment));
			PaymentTransaction doneNullTime = transaction("txn-14", "20260921-NULLCURR1", "DONE", null);
			PaymentTransaction canceled = transaction("txn-15", "20260921-NULLCURR1", "CANCELED", FROM.plusHours(1));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(doneNullTime, canceled), FROM, TO);

			// then: DONE 이 남았다면 DB CANCELED 와 불일치해 조회가 났을 것이다.
			assertThat(report.matched()).isEqualTo(1);
			verifyNoInteractions(paymentClient, alertNotifier, lateResultApplier);
		}

		@Test
		@DisplayName("같은 시각에 두 행 모두 취소 계열이면 나중에 온 행으로 덮어쓰지 않는다")
		void keepsCurrentWhenBothTransactionsAreCancelLike() {
			// given: 부분 취소(current)가 먼저 들어오고, 같은 시각의 전체 취소(candidate)가 뒤이어 온다.
			Payment payment = payment(18L, "20260921-BOTHCANCEL1", PaymentStatus.CANCELED);
			given(paymentRepository.findByTossOrderId("20260921-BOTHCANCEL1")).willReturn(Optional.of(payment));
			PaymentTransaction partial = transaction("txn-18", "20260921-BOTHCANCEL1", "PARTIAL_CANCELED",
					FROM.plusHours(1));
			PaymentTransaction canceled = transaction("txn-19", "20260921-BOTHCANCEL1", "CANCELED",
					FROM.plusHours(1));
			given(paymentClient.lookup("20260921-BOTHCANCEL1")).willReturn(lookup(PaymentLookupStatus.ABORTED));

			// when: 부분 취소가 먼저(current), 전체 취소가 나중(candidate) - 둘 다 취소 계열이라 먼저 온 쪽을 유지한다.
			PaymentSettlementReport report = service.reconcile(List.of(partial, canceled), FROM, TO);

			// then: current(부분 취소)가 유지됐다면 DB CANCELED 와도 matches() 표에서 불일치라 조회가 났을 것이다.
			assertThat(report.mismatched()).isEqualTo(1);
			verify(paymentClient).lookup("20260921-BOTHCANCEL1");
		}

		@Test
		@DisplayName("두 행 모두 시각이 없으면 취소 계열을 우선한다")
		void picksCancelLikeWhenBothTransactionAtAreNull() {
			// given
			Payment payment = payment(11L, "20260921-BOTHNULL1", PaymentStatus.CANCELED);
			given(paymentRepository.findByTossOrderId("20260921-BOTHNULL1")).willReturn(Optional.of(payment));
			PaymentTransaction done = transaction("txn-16", "20260921-BOTHNULL1", "DONE", null);
			PaymentTransaction canceled = transaction("txn-17", "20260921-BOTHNULL1", "CANCELED", null);

			// when
			PaymentSettlementReport report = service.reconcile(List.of(done, canceled), FROM, TO);

			// then
			assertThat(report.matched()).isEqualTo(1);
			verifyNoInteractions(paymentClient, alertNotifier, lateResultApplier);
		}

		@ParameterizedTest(name = "DB {0} 이면 불일치 시 재조회 결과를 바로 lateResultApplier 에 위임한다")
		@EnumSource(value = PaymentStatus.class, names = { "READY", "UNKNOWN", "CANCEL_REQUESTED" })
		@DisplayName("DB 가 아직 확정되지 않은 상태면 토스 상태와 무관하게 항상 재조회 뒤 lateResultApplier 에 위임한다")
		void appliesForUnresolvedDbStatuses(PaymentStatus status) {
			// given
			String tossOrderId = "20260921-UNRES-" + status;
			Payment payment = payment(12L, tossOrderId, status);
			given(paymentRepository.findByTossOrderId(tossOrderId)).willReturn(Optional.of(payment));
			PaymentTransaction transaction = transaction("txn-unres-" + status, tossOrderId, "DONE",
					FROM.plusHours(1));
			PaymentLookupResult lookup = lookup(PaymentLookupStatus.DONE);
			given(paymentClient.lookup(tossOrderId)).willReturn(lookup);

			// when
			PaymentSettlementReport report = service.reconcile(List.of(transaction), FROM, TO);

			// then
			assertThat(report.applied()).isEqualTo(1);
			verify(lateResultApplier).apply(any(), eq(lookup), eq("settlement"));
		}

		@ParameterizedTest(name = "토스 상태가 {0} 이면 조회 없이 matched 로 센다")
		@ValueSource(strings = { "ABORTED", "EXPIRED" })
		@DisplayName("DB FAILED 는 토스 ABORTED·EXPIRED 둘 다 일치로 본다")
		void treatsFailedAsMatchedForAbortedOrExpired(String tossStatus) {
			// given
			String tossOrderId = "20260921-FAILMATCH-" + tossStatus;
			Payment payment = payment(13L, tossOrderId, PaymentStatus.FAILED);
			given(paymentRepository.findByTossOrderId(tossOrderId)).willReturn(Optional.of(payment));
			PaymentTransaction transaction = transaction("txn-failmatch-" + tossStatus, tossOrderId, tossStatus,
					FROM.plusHours(1));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(transaction), FROM, TO);

			// then
			assertThat(report.matched()).isEqualTo(1);
			verifyNoInteractions(paymentClient, alertNotifier, lateResultApplier);
		}

		@Test
		@DisplayName("거래 목록에서 이미 처리한 orderId 는 DB 승인 결제 목록에도 있어도 다시 세지 않는다")
		void skipsApprovedPaymentAlreadyHandledByTransactionList() {
			// given
			Payment payment = payment(14L, "20260921-BOTH0001", PaymentStatus.DONE);
			given(paymentRepository.findByTossOrderId("20260921-BOTH0001")).willReturn(Optional.of(payment));
			given(paymentRepository.findByApprovedAtGreaterThanEqualAndApprovedAtLessThan(FROM, TO))
					.willReturn(List.of(payment));
			PaymentTransaction transaction = transaction("txn-both", "20260921-BOTH0001", "DONE", FROM.plusHours(1));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(transaction), FROM, TO);

			// then
			assertThat(report.matched()).isEqualTo(1);
			assertThat(report.mismatched()).isZero();
			assertThat(report.applied()).isZero();
			verifyNoInteractions(paymentClient);
		}

		@Test
		@DisplayName("DB 승인 결제 목록에 있어도 상태가 대상 밖이면 건너뛴다")
		void skipsApprovedPaymentWithNonTargetStatus() {
			// given
			Payment payment = payment(15L, "20260921-SKIP0001", PaymentStatus.READY);
			given(paymentRepository.findByApprovedAtGreaterThanEqualAndApprovedAtLessThan(FROM, TO))
					.willReturn(List.of(payment));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(), FROM, TO);

			// then
			assertThat(report.matched()).isZero();
			assertThat(report.applied()).isZero();
			assertThat(report.mismatched()).isZero();
			assertThat(report.unknown()).isZero();
			assertThat(report.failed()).isZero();
			verifyNoInteractions(paymentClient, lateResultApplier, alertNotifier);
		}

		@Test
		@DisplayName("DB 승인 결제 목록 처리 중 한 건이 예외를 던져도 나머지는 계속 처리한다")
		void continuesProcessingApprovedPaymentsWhenOneThrows() {
			// given
			Payment boom = payment(16L, "20260921-DBBOOM01", PaymentStatus.DONE);
			Payment ok = payment(17L, "20260921-DBOK0001", PaymentStatus.DONE);
			given(paymentRepository.findByApprovedAtGreaterThanEqualAndApprovedAtLessThan(FROM, TO))
					.willReturn(List.of(boom, ok));
			given(paymentClient.lookup("20260921-DBBOOM01")).willThrow(new RuntimeException("TOSS 통신 실패"));
			given(paymentClient.lookup("20260921-DBOK0001")).willReturn(lookup(PaymentLookupStatus.DONE));

			// when
			PaymentSettlementReport report = service.reconcile(List.of(), FROM, TO);

			// then
			assertThat(report.failed()).isEqualTo(1);
			assertThat(report.matched()).isEqualTo(1);
		}
	}

	private Payment payment(Long id, String tossOrderId, PaymentStatus status) {
		Order order = OrderFixture.withId(OrderFixture.create(MemberFixture.create(), tossOrderId), ORDER_ID);
		Payment payment = PaymentFixture.withStatus(Payment.ready(order), status);
		ReflectionTestUtils.setField(payment, "id", id);
		return payment;
	}

	private PaymentTransaction transaction(String transactionKey, String tossOrderId, String status,
			LocalDateTime transactionAt) {
		return new PaymentTransaction(transactionKey, "pk-" + transactionKey, tossOrderId, status, transactionAt);
	}

	private PaymentLookupResult lookup(PaymentLookupStatus status) {
		return new PaymentLookupResult(status, "pk-lookup", "카드", null, FROM.plusHours(1), null);
	}
}
