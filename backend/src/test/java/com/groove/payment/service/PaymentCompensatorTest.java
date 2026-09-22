package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.groove.fixture.PaymentFixture;
import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.entity.Payment;

@ExtendWith(MockitoExtension.class)
class PaymentCompensatorTest {

	private static final Long PAYMENT_ID = 10L;
	private static final Long ORDER_ID = 500L;
	private static final String TOSS_ORDER_ID = "20260913-K7Q2M9XZ";
	private static final String REASON = PaymentCompensator.ORDER_INVALIDATED_REASON;
	private static final String DUPLICATE_REASON = PaymentCompensator.DUPLICATE_APPROVAL_REASON;

	@Mock
	PaymentClient paymentClient;

	@Mock
	PaymentConfirmWriter writer;

	@Mock
	PaymentCompensationWriter compensationWriter;

	@Mock
	AlertNotifier alertNotifier;

	PaymentCompensator compensator;

	Clock clock;
	LocalDateTime now;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-13T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		compensator = new PaymentCompensator(paymentClient, writer, compensationWriter, clock, alertNotifier);
	}

	@Nested
	@DisplayName("cancelApproved()")
	class CancelApproved {

		@Test
		@DisplayName("토스 취소가 성공하면 결제를 보상 취소로 기록하고 취소됨을 반환한다")
		void marksCompensatedAndReturnsCanceledWhenTossCancelSucceeds() {
			// given
			LocalDateTime canceledAt = now.truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.cancel(PaymentFixture.PAYMENT_KEY, REASON))
					.willReturn(new PaymentCancelResult(PaymentFixture.PAYMENT_KEY, "CANCELED", canceledAt));

			// when
			CompensationResult result = compensator.cancelApproved(PAYMENT_ID, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, REASON);

			// then
			assertThat(result.canceled()).isTrue();
			assertThat(result.canceledAt()).isEqualTo(canceledAt);
			verify(writer).markCompensated(PAYMENT_ID, PaymentFixture.PAYMENT_KEY, PaymentFixture.APPROVED_AT,
					canceledAt, REASON);
		}

		@Test
		@DisplayName("토스 응답에 취소 시각이 없으면 서버 시각을 사용한다")
		void usesServerTimeWhenCanceledAtMissing() {
			// given
			given(paymentClient.cancel(PaymentFixture.PAYMENT_KEY, REASON))
					.willReturn(new PaymentCancelResult(PaymentFixture.PAYMENT_KEY, "CANCELED", null));

			// when
			CompensationResult result = compensator.cancelApproved(PAYMENT_ID, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, REASON);

			// then
			assertThat(result.canceledAt()).isEqualTo(now);
			verify(writer).markCompensated(PAYMENT_ID, PaymentFixture.PAYMENT_KEY, PaymentFixture.APPROVED_AT, now,
					REASON);
		}

		@Test
		@DisplayName("paymentId 가 없으면 대기 행을 먼저 커밋한 뒤 토스를 취소하고 완료로 기록한다")
		void enqueuesBeforeCancelingAndCompletesWhenPaymentIdIsNull() {
			// given
			LocalDateTime canceledAt = now.truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.cancel(PaymentFixture.PAYMENT_KEY, DUPLICATE_REASON))
					.willReturn(new PaymentCancelResult(PaymentFixture.PAYMENT_KEY, "CANCELED", canceledAt));

			// when
			CompensationResult result = compensator.cancelApproved(null, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, DUPLICATE_REASON, ORDER_ID, TOSS_ORDER_ID);

			// then
			assertThat(result.canceled()).isTrue();
			InOrder order = inOrder(compensationWriter, paymentClient);
			order.verify(compensationWriter).enqueue(PaymentFixture.PAYMENT_KEY, ORDER_ID, TOSS_ORDER_ID,
					PaymentFixture.APPROVED_AT, DUPLICATE_REASON);
			order.verify(paymentClient).cancel(PaymentFixture.PAYMENT_KEY, DUPLICATE_REASON);
			verify(compensationWriter).complete(PaymentFixture.PAYMENT_KEY, canceledAt);
			verify(writer, never()).markCompensated(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("paymentId 가 없으면 취소는 성공했으나 보상 대기 완료 기록이 실패해도 취소됨을 반환하고 경보를 보낸다")
		void returnsCanceledAndNotifiesAlertWhenCompensationCompleteFailsAndPaymentIdIsNull() {
			// given
			LocalDateTime canceledAt = now.truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.cancel(PaymentFixture.PAYMENT_KEY, DUPLICATE_REASON))
					.willReturn(new PaymentCancelResult(PaymentFixture.PAYMENT_KEY, "CANCELED", canceledAt));
			willThrow(new IllegalStateException("보상 대기 행을 찾을 수 없습니다")).given(compensationWriter)
					.complete(PaymentFixture.PAYMENT_KEY, canceledAt);

			// when
			CompensationResult result = compensator.cancelApproved(null, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, DUPLICATE_REASON, ORDER_ID, TOSS_ORDER_ID);

			// then
			assertThat(result.canceled()).isTrue();
			verify(alertNotifier).notify(any(Alert.class));
		}

		@Test
		@DisplayName("토스 취소는 성공했으나 보상 기록이 실패해도 취소됨을 반환한다")
		void returnsCanceledEvenWhenMarkCompensatedFails() {
			// given
			LocalDateTime canceledAt = now.truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.cancel(PaymentFixture.PAYMENT_KEY, REASON))
					.willReturn(new PaymentCancelResult(PaymentFixture.PAYMENT_KEY, "CANCELED", canceledAt));
			willThrow(new CannotAcquireLockException("lock timeout")).given(writer)
					.markCompensated(eq(PAYMENT_ID), eq(PaymentFixture.PAYMENT_KEY), any(), any(), anyString());

			// when
			CompensationResult result = compensator.cancelApproved(PAYMENT_ID, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, REASON);

			// then
			assertThat(result.canceled()).isTrue();
		}

		@Test
		@DisplayName("토스 취소가 거절되면 결제를 결과 불명으로 남기고 취소되지 않음을 반환한다")
		void marksUnknownAndReturnsNotCanceledWhenTossCancelRejected() {
			// given
			BusinessException cancelFailed = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED,
					"TOSS ALREADY_CANCELED_PAYMENT");
			willThrow(cancelFailed).given(paymentClient).cancel(PaymentFixture.PAYMENT_KEY, REASON);

			// when
			CompensationResult result = compensator.cancelApproved(PAYMENT_ID, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, REASON);

			// then
			assertThat(result.canceled()).isFalse();
			assertThat(result.failureDetail()).isEqualTo(cancelFailed.getMessage());
			verify(writer).markUnknown(PAYMENT_ID, "보상 취소 실패: " + cancelFailed.getMessage());
			verify(alertNotifier).notify(any(Alert.class));
		}

		@Test
		@DisplayName("토스 취소 결과가 불명이어도 결제를 결과 불명으로 남기고 취소되지 않음을 반환한다")
		void marksUnknownAndReturnsNotCanceledWhenTossCancelResultUnknown() {
			// given
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS 통신 실패: Read timed out");
			willThrow(resultUnknown).given(paymentClient).cancel(PaymentFixture.PAYMENT_KEY, REASON);

			// when
			CompensationResult result = compensator.cancelApproved(PAYMENT_ID, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, REASON);

			// then
			assertThat(result.canceled()).isFalse();
			verify(writer).markUnknown(PAYMENT_ID, "보상 취소 실패: " + resultUnknown.getMessage());
		}

		@Test
		@DisplayName("paymentId 가 없으면 취소가 명확히 거절돼도 상한을 기다리지 않고 즉시 수동 확인으로 넘긴다")
		void reviewsManuallyWhenPaymentIdIsNullAndCancelExplicitlyRejected() {
			// given
			BusinessException cancelFailed = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED,
					"TOSS ALREADY_CANCELED_PAYMENT");
			willThrow(cancelFailed).given(paymentClient).cancel(PaymentFixture.PAYMENT_KEY, DUPLICATE_REASON);

			// when
			CompensationResult result = compensator.cancelApproved(null, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, DUPLICATE_REASON, ORDER_ID, TOSS_ORDER_ID);

			// then
			assertThat(result.canceled()).isFalse();
			verify(compensationWriter).enqueue(PaymentFixture.PAYMENT_KEY, ORDER_ID, TOSS_ORDER_ID,
					PaymentFixture.APPROVED_AT, DUPLICATE_REASON);
			verify(compensationWriter).reviewManually(PaymentFixture.PAYMENT_KEY,
					"보상 취소 실패: " + cancelFailed.getMessage());
			verify(compensationWriter, never()).fail(any(), any());
			verify(writer, never()).markUnknown(any(), any());
		}

		@Test
		@DisplayName("paymentId 가 없고 수동 확인 기록마저 실패하면 경보를 보낸다")
		void notifiesAlertWhenReviewManuallyAlsoFailsAndPaymentIdIsNull() {
			// given
			BusinessException cancelFailed = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED,
					"TOSS ALREADY_CANCELED_PAYMENT");
			willThrow(cancelFailed).given(paymentClient).cancel(PaymentFixture.PAYMENT_KEY, DUPLICATE_REASON);
			willThrow(new IllegalStateException("보상 대기 행을 찾을 수 없습니다")).given(compensationWriter)
					.reviewManually(eq(PaymentFixture.PAYMENT_KEY), anyString());

			// when
			CompensationResult result = compensator.cancelApproved(null, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, DUPLICATE_REASON, ORDER_ID, TOSS_ORDER_ID);

			// then: 승인 후 보상 취소 실패 경보(외부 catch) + 수동 확인 기록 실패 경보(내부 catch), 둘 다 온다
			assertThat(result.canceled()).isFalse();
			verify(alertNotifier, times(2)).notify(any(Alert.class));
		}

		@Test
		@DisplayName("paymentId 가 없으면 취소 결과가 불명일 때만 재시도 횟수를 올린다")
		void failsWhenPaymentIdIsNullAndCancelResultUnknown() {
			// given
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS 통신 실패: Read timed out");
			willThrow(resultUnknown).given(paymentClient).cancel(PaymentFixture.PAYMENT_KEY, DUPLICATE_REASON);

			// when
			CompensationResult result = compensator.cancelApproved(null, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, DUPLICATE_REASON, ORDER_ID, TOSS_ORDER_ID);

			// then
			assertThat(result.canceled()).isFalse();
			verify(compensationWriter).fail(PaymentFixture.PAYMENT_KEY, "보상 취소 실패: " + resultUnknown.getMessage());
			verify(compensationWriter, never()).reviewManually(any(), any());
		}

		@Test
		@DisplayName("paymentId 가 없고 실패 기록마저 실패하면 경보를 보낸다")
		void notifiesAlertWhenCompensationFailAlsoFailsAndPaymentIdIsNull() {
			// given
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS 통신 실패: Read timed out");
			willThrow(resultUnknown).given(paymentClient).cancel(PaymentFixture.PAYMENT_KEY, DUPLICATE_REASON);
			willThrow(new IllegalStateException("보상 대기 행을 찾을 수 없습니다")).given(compensationWriter)
					.fail(eq(PaymentFixture.PAYMENT_KEY), anyString());

			// when
			CompensationResult result = compensator.cancelApproved(null, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, DUPLICATE_REASON, ORDER_ID, TOSS_ORDER_ID);

			// then: 승인 후 보상 취소 실패 경보(외부 catch) + 실패 기록 실패 경보(내부 catch), 둘 다 온다
			assertThat(result.canceled()).isFalse();
			verify(alertNotifier, times(2)).notify(any(Alert.class));
		}

		@Test
		@DisplayName("4-인자 오버로드에 paymentId 가 없으면 NullPointerException 을 던지고 아무것도 건드리지 않는다")
		void throwsNullPointerExceptionWhenPaymentIdIsNullOnFourArgOverload() {
			// when & then
			assertThatThrownBy(() -> compensator.cancelApproved(null, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, REASON))
					.isInstanceOf(NullPointerException.class);
			verifyNoInteractions(paymentClient, compensationWriter, writer);
		}

		@Test
		@DisplayName("결과 불명 기록 중 승인이 먼저 커밋돼 버전이 충돌해도 취소되지 않음을 반환한다")
		void returnsNotCanceledEvenWhenMarkUnknownVersionConflicts() {
			// given
			BusinessException cancelFailed = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED,
					"TOSS ALREADY_CANCELED_PAYMENT");
			willThrow(cancelFailed).given(paymentClient).cancel(PaymentFixture.PAYMENT_KEY, REASON);
			willThrow(new ObjectOptimisticLockingFailureException(Payment.class, PAYMENT_ID)).given(writer)
					.markUnknown(eq(PAYMENT_ID), anyString());

			// when
			CompensationResult result = compensator.cancelApproved(PAYMENT_ID, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, REASON);

			// then
			assertThat(result.canceled()).isFalse();
		}
	}
}
