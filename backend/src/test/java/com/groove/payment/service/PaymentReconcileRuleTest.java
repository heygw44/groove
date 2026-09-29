package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.groove.order.entity.OrderStatus;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.entity.PaymentStatus;

class PaymentReconcileRuleTest {

	private static final BigDecimal AMOUNT = new BigDecimal("30000");
	private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 9, 13, 10, 0);
	private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 9, 13, 11, 0);

	@Nested
	@DisplayName("decide()")
	class Decide {

		@Test
		@DisplayName("토스가 DONE 이고 금액이 일치하고 주문이 PENDING 이면 APPROVE 한다")
		void approvesWhenDoneAndAmountMatchesAndOrderPending() {
			// given
			PaymentLookupResult lookup = doneLookup(AMOUNT);

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(PaymentStatus.READY, OrderStatus.PENDING,
					AMOUNT, BigDecimal.ZERO, lookup);

			// then
			assertThat(decision).isEqualTo(PaymentReconcileDecision.APPROVE);
		}

		@Test
		@DisplayName("토스가 DONE 이고 금액이 일치하고 주문이 CANCELED 면 COMPENSATE 한다")
		void compensatesWhenDoneAndAmountMatchesAndOrderCanceled() {
			// given
			PaymentLookupResult lookup = doneLookup(AMOUNT);

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(PaymentStatus.READY, OrderStatus.CANCELED,
					AMOUNT, BigDecimal.ZERO, lookup);

			// then
			assertThat(decision).isEqualTo(PaymentReconcileDecision.COMPENSATE);
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, names = {"PENDING", "CANCELED"}, mode = EnumSource.Mode.EXCLUDE)
		@DisplayName("토스가 DONE 이고 금액이 일치해도 주문이 PENDING/CANCELED 가 아니면 MANUAL_REVIEW 한다")
		void manualReviewsWhenDoneAndAmountMatchesButOrderOtherwise(OrderStatus orderStatus) {
			// given
			PaymentLookupResult lookup = doneLookup(AMOUNT);

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(PaymentStatus.READY, orderStatus, AMOUNT,
					BigDecimal.ZERO, lookup);

			// then
			assertThat(decision).isEqualTo(PaymentReconcileDecision.MANUAL_REVIEW);
		}

		@ParameterizedTest
		@EnumSource(OrderStatus.class)
		@DisplayName("토스가 DONE 이어도 금액이 다르면 주문 상태보다 먼저 MANUAL_REVIEW 로 판정한다")
		void manualReviewsWhenDoneAndAmountMismatchRegardlessOfOrderStatus(OrderStatus orderStatus) {
			// given
			PaymentLookupResult lookup = doneLookup(new BigDecimal("99999"));

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(PaymentStatus.READY, orderStatus, AMOUNT,
					BigDecimal.ZERO, lookup);

			// then
			assertThat(decision).isEqualTo(PaymentReconcileDecision.MANUAL_REVIEW);
		}

		@Test
		@DisplayName("토스가 DONE 인데 totalAmount 가 없으면 MANUAL_REVIEW 한다")
		void manualReviewsWhenDoneAndTotalAmountMissing() {
			// given
			PaymentLookupResult lookup = new PaymentLookupResult(PaymentLookupStatus.DONE, "key", "카드", null,
					APPROVED_AT, null);

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(PaymentStatus.READY, OrderStatus.PENDING,
					AMOUNT, BigDecimal.ZERO, lookup);

			// then
			assertThat(decision).isEqualTo(PaymentReconcileDecision.MANUAL_REVIEW);
		}

		@ParameterizedTest
		@CsvSource({"NOT_FOUND", "ABORTED", "EXPIRED"})
		@DisplayName("토스 조회가 없음/중단/만료면 주문 상태와 무관하게 FAIL 한다")
		void failsWhenLookupIsTerminalFailure(PaymentLookupStatus status) {
			// given
			PaymentLookupResult lookup = lookupOf(status);

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(PaymentStatus.READY, OrderStatus.PENDING,
					AMOUNT, BigDecimal.ZERO, lookup);

			// then
			assertThat(decision).isEqualTo(PaymentReconcileDecision.FAIL);
		}

		@ParameterizedTest
		@CsvSource({"READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT"})
		@DisplayName("토스가 아직 진행 중이면 SKIP 한다")
		void skipsWhenLookupIsInProgress(PaymentLookupStatus status) {
			// given
			PaymentLookupResult lookup = lookupOf(status);

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(PaymentStatus.READY, OrderStatus.PENDING,
					AMOUNT, BigDecimal.ZERO, lookup);

			// then
			assertThat(decision).isEqualTo(PaymentReconcileDecision.SKIP);
		}

		@Test
		@DisplayName("토스에서 이미 취소된 결제면 SYNC_CANCELED 한다")
		void syncCancelsWhenLookupCanceled() {
			// given
			PaymentLookupResult lookup = lookupOf(PaymentLookupStatus.CANCELED);

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(PaymentStatus.READY, OrderStatus.PENDING,
					AMOUNT, BigDecimal.ZERO, lookup);

			// then
			assertThat(decision).isEqualTo(PaymentReconcileDecision.SYNC_CANCELED);
		}

		@Test
		@DisplayName("부분 취소면 MANUAL_REVIEW 한다")
		void manualReviewsWhenPartialCanceled() {
			// given
			PaymentLookupResult lookup = lookupOf(PaymentLookupStatus.PARTIAL_CANCELED);

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(PaymentStatus.READY, OrderStatus.PENDING,
					AMOUNT, BigDecimal.ZERO, lookup);

			// then
			assertThat(decision).isEqualTo(PaymentReconcileDecision.MANUAL_REVIEW);
		}

		@ParameterizedTest
		@CsvSource({
			"CANCELED, PAID, COMPLETE_CANCEL",
			"CANCELED, PREPARING, COMPLETE_CANCEL",
			"DONE, PAID, RETRY_CANCEL",
			"DONE, PREPARING, RETRY_CANCEL",
			"PARTIAL_CANCELED, PAID, MANUAL_REVIEW",
			"NOT_FOUND, PAID, MANUAL_REVIEW",
			"CANCELED, PENDING, MANUAL_REVIEW",
			"DONE, CANCELED, MANUAL_REVIEW"
		})
		@DisplayName("CANCEL_REQUESTED 는 토스·주문 상태 표에 따라 취소 완료·재시도·수동 확인을 결정한다")
		void decidesCancelRequested(PaymentLookupStatus lookupStatus, OrderStatus orderStatus,
				PaymentReconcileDecision expected) {
			// given
			PaymentLookupResult lookup = lookupOf(lookupStatus);

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(PaymentStatus.CANCEL_REQUESTED,
					orderStatus, AMOUNT, BigDecimal.ZERO, lookup);

			// then
			assertThat(decision).isEqualTo(expected);
		}

		@ParameterizedTest(name = "결제={0}, 토스={1}, 취소잔액={2}, 남은금액={3} → {4}")
		@CsvSource({
			"DONE, PARTIAL_CANCELED, 10000, 10000, SKIP",
			"DONE, PARTIAL_CANCELED, 9999, 10000, MANUAL_REVIEW",
			"PARTIAL_CANCELED, PARTIAL_CANCELED, 5000, 5000, SKIP",
			"PARTIAL_CANCELED, CANCELED, 0, 0, SKIP",
			"PARTIAL_CANCELED, CANCELED, 100, 0, MANUAL_REVIEW",
			"DONE, CANCELED, 0, 10000, MANUAL_REVIEW"
		})
		@DisplayName("DONE/PARTIAL_CANCELED 인데 토스가 PARTIAL_CANCELED/CANCELED 면 취소 잔액을 맞춰본다")
		void decidesPartialCancelDrift(PaymentStatus paymentStatus, PaymentLookupStatus lookupStatus,
				BigDecimal tossBalance, BigDecimal expectedRemaining, PaymentReconcileDecision expected) {
			// given
			BigDecimal canceledAmount = AMOUNT.subtract(expectedRemaining);
			PaymentLookupResult lookup = lookupWithBalance(lookupStatus, tossBalance);

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(paymentStatus, OrderStatus.PAID, AMOUNT,
					canceledAmount, lookup);

			// then
			assertThat(decision).isEqualTo(expected);
		}

		@Test
		@DisplayName("취소 잔액을 확인할 balanceAmount 가 없으면 MANUAL_REVIEW 한다")
		void manualReviewsWhenBalanceAmountMissing() {
			// given
			PaymentLookupResult lookup = lookupOf(PaymentLookupStatus.PARTIAL_CANCELED);

			// when
			PaymentReconcileDecision decision = PaymentReconcileRule.decide(PaymentStatus.DONE, OrderStatus.PAID,
					AMOUNT, BigDecimal.ZERO, lookup);

			// then
			assertThat(decision).isEqualTo(PaymentReconcileDecision.MANUAL_REVIEW);
		}
	}

	private PaymentLookupResult doneLookup(BigDecimal totalAmount) {
		return new PaymentLookupResult(PaymentLookupStatus.DONE, "key", "카드", totalAmount, APPROVED_AT, null);
	}

	private PaymentLookupResult lookupOf(PaymentLookupStatus status) {
		return new PaymentLookupResult(status, "key", "카드", AMOUNT, APPROVED_AT, CANCELED_AT);
	}

	private PaymentLookupResult lookupWithBalance(PaymentLookupStatus status, BigDecimal balance) {
		return new PaymentLookupResult(status, "key", "카드", AMOUNT, APPROVED_AT, CANCELED_AT, balance, "txn-key");
	}
}
