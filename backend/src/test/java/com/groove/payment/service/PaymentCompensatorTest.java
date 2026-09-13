package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.groove.fixture.PaymentFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.entity.Payment;

@ExtendWith(MockitoExtension.class)
class PaymentCompensatorTest {

	private static final Long PAYMENT_ID = 10L;
	private static final String REASON = PaymentCompensator.ORDER_INVALIDATED_REASON;

	@Mock
	PaymentClient paymentClient;

	@Mock
	PaymentConfirmWriter writer;

	PaymentCompensator compensator;

	Clock clock;
	LocalDateTime now;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-13T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		compensator = new PaymentCompensator(paymentClient, writer, clock);
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
		@DisplayName("paymentId 가 없으면 토스 취소만 하고 DB 행은 건드리지 않는다")
		void doesNotWriteToDatabaseWhenPaymentIdIsNull() {
			// given
			LocalDateTime canceledAt = now.truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.cancel(PaymentFixture.PAYMENT_KEY, REASON))
					.willReturn(new PaymentCancelResult(PaymentFixture.PAYMENT_KEY, "CANCELED", canceledAt));

			// when
			CompensationResult result = compensator.cancelApproved(null, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, REASON);

			// then
			assertThat(result.canceled()).isTrue();
			verify(writer, never()).markCompensated(any(), any(), any(), any(), any());
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
		@DisplayName("paymentId 가 없으면 취소가 거절돼도 결과 불명 기록을 시도하지 않는다")
		void doesNotMarkUnknownWhenPaymentIdIsNullAndCancelRejected() {
			// given
			BusinessException cancelFailed = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED,
					"TOSS ALREADY_CANCELED_PAYMENT");
			willThrow(cancelFailed).given(paymentClient).cancel(PaymentFixture.PAYMENT_KEY, REASON);

			// when
			CompensationResult result = compensator.cancelApproved(null, PaymentFixture.PAYMENT_KEY,
					PaymentFixture.APPROVED_AT, REASON);

			// then
			assertThat(result.canceled()).isFalse();
			verify(writer, never()).markUnknown(any(), any());
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
