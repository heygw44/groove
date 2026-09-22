package com.groove.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.dto.PaymentCompensationCandidate;

@ExtendWith(MockitoExtension.class)
class PaymentCompensationRetrierTest {

	@Mock
	private PaymentClient paymentClient;

	@Mock
	private PaymentCompensationWriter compensationWriter;

	private PaymentCompensationRetrier retrier;

	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-22T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		retrier = new PaymentCompensationRetrier(paymentClient, compensationWriter, clock);
	}

	@Nested
	@DisplayName("retry()")
	class Retry {

		@Test
		@DisplayName("취소가 성공하면 완료로 기록한다")
		void completesWhenCancelSucceeds() {
			// given
			PaymentCompensationCandidate candidate = new PaymentCompensationCandidate("tviva-dup", "중복 승인 자동 취소");
			given(paymentClient.cancel("tviva-dup", "중복 승인 자동 취소"))
					.willReturn(new PaymentCancelResult("tviva-dup", "CANCELED", now));

			// when
			retrier.retry(candidate);

			// then
			verify(compensationWriter).complete("tviva-dup", now);
		}

		@Test
		@DisplayName("취소 결과가 불명이면 재시도 횟수만 올린다")
		void failsWhenCancelResultUnknown() {
			// given
			PaymentCompensationCandidate candidate = new PaymentCompensationCandidate("tviva-dup", "중복 승인 자동 취소");
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS 통신 실패: Read timed out");
			given(paymentClient.cancel("tviva-dup", "중복 승인 자동 취소")).willThrow(resultUnknown);

			// when
			retrier.retry(candidate);

			// then
			verify(compensationWriter).fail("tviva-dup", resultUnknown.getMessage());
			verify(compensationWriter, never()).reviewManually(any(), any());
		}

		@Test
		@DisplayName("취소가 명확히 거절되면 상한을 기다리지 않고 즉시 수동 확인으로 넘긴다")
		void reviewsManuallyWhenCancelRejected() {
			// given
			PaymentCompensationCandidate candidate = new PaymentCompensationCandidate("tviva-dup", "중복 승인 자동 취소");
			BusinessException rejection = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, "TOSS 거절");
			given(paymentClient.cancel("tviva-dup", "중복 승인 자동 취소")).willThrow(rejection);

			// when
			retrier.retry(candidate);

			// then
			verify(compensationWriter).reviewManually("tviva-dup", rejection.getMessage());
			verify(compensationWriter, never()).fail(any(), any());
		}

		@Test
		@DisplayName("취소 호출이 예상 못한 예외를 던지면 재시도 횟수만 올린다")
		void failsWhenCancelThrowsUnexpectedException() {
			// given
			PaymentCompensationCandidate candidate = new PaymentCompensationCandidate("tviva-dup", "중복 승인 자동 취소");
			RuntimeException timeout = new RuntimeException("Read timed out");
			given(paymentClient.cancel("tviva-dup", "중복 승인 자동 취소")).willThrow(timeout);

			// when
			retrier.retry(candidate);

			// then
			verify(compensationWriter).fail("tviva-dup", timeout.getMessage());
		}
	}
}
