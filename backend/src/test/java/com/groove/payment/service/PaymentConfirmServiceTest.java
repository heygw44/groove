package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.groove.fixture.PaymentFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.dto.PaymentConfirmResponse;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long PAYMENT_ID = 10L;
	private static final Long ORDER_ID = 500L;
	private static final BigDecimal AMOUNT = new BigDecimal("45000");
	private static final String ORDER_NUMBER = "20260904-TESTAB12";

	@Mock
	PaymentConfirmWriter writer;

	@Mock
	PaymentClient paymentClient;

	@Mock
	PaymentCompensator compensator;

	PaymentConfirmService service;

	PaymentConfirmRequest request;

	ConfirmPreparation preparation;

	@BeforeEach
	void setUp() {
		service = new PaymentConfirmService(writer, paymentClient, compensator);
		request = new PaymentConfirmRequest(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT.longValueExact());
		preparation = new ConfirmPreparation(PAYMENT_ID, ORDER_ID, ORDER_NUMBER, AMOUNT, Optional.empty());
	}

	private ObjectOptimisticLockingFailureException versionConflict() {
		return new ObjectOptimisticLockingFailureException(Payment.class, PAYMENT_ID);
	}

	@Nested
	@DisplayName("confirm()")
	class Confirm {

		@Test
		@DisplayName("이미 승인된 요청이면 토스를 호출하지 않고 기존 응답을 그대로 반환한다")
		void returnsExistingResponseWithoutCallingClientWhenAlreadyApproved() {
			// given
			PaymentConfirmResponse alreadyApproved = new PaymentConfirmResponse(PAYMENT_ID, ORDER_ID, ORDER_NUMBER,
					PaymentStatus.DONE, PaymentFixture.METHOD, AMOUNT, PaymentFixture.APPROVED_AT);
			ConfirmPreparation alreadyApprovedPreparation = new ConfirmPreparation(PAYMENT_ID, ORDER_ID, ORDER_NUMBER,
					AMOUNT, Optional.of(alreadyApproved));
			given(writer.prepare(MEMBER_ID, request)).willReturn(alreadyApprovedPreparation);

			// when
			PaymentConfirmResponse response = service.confirm(MEMBER_ID, request);

			// then
			assertThat(response).isEqualTo(alreadyApproved);
			verify(paymentClient, never()).confirm(anyString(), anyString(), any());
		}

		@Test
		@DisplayName("정상 승인이면 토스를 호출하고 결과를 저장한다")
		void confirmsAndApprovesOnSuccess() {
			// given
			given(writer.prepare(MEMBER_ID, request)).willReturn(preparation);
			PaymentConfirmResult result = new PaymentConfirmResult(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER,
					PaymentFixture.METHOD, AMOUNT, LocalDateTime.now());
			given(paymentClient.confirm(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT)).willReturn(result);
			PaymentConfirmResponse approvedResponse = new PaymentConfirmResponse(PAYMENT_ID, ORDER_ID, ORDER_NUMBER,
					PaymentStatus.DONE, PaymentFixture.METHOD, AMOUNT, PaymentFixture.APPROVED_AT);
			given(writer.approve(eq(ORDER_ID), eq(PAYMENT_ID), eq(PaymentFixture.PAYMENT_KEY), eq(result)))
					.willReturn(approvedResponse);

			// when
			PaymentConfirmResponse response = service.confirm(MEMBER_ID, request);

			// then
			assertThat(response).isEqualTo(approvedResponse);
			verify(writer, never()).fail(any(), any());
			verify(writer, never()).markUnknown(any(), any());
			verify(compensator, never()).cancelApproved(any(), any(), any(), any());
		}

		@Test
		@DisplayName("토스 승인이 명시적으로 거절되면 실패 사유를 저장하고 같은 예외를 다시 던진다")
		void marksFailureAndRethrowsWhenClientRejects() {
			// given
			given(writer.prepare(MEMBER_ID, request)).willReturn(preparation);
			BusinessException confirmFailed = new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED,
					"TOSS REJECT_CARD_COMPANY: 카드사에서 승인을 거절했습니다.");
			willThrow(confirmFailed).given(paymentClient).confirm(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT);

			// when & then
			assertThatThrownBy(() -> service.confirm(MEMBER_ID, request))
					.isSameAs(confirmFailed);
			verify(writer).fail(PAYMENT_ID, confirmFailed.getMessage());
			verify(writer, never()).markUnknown(any(), any());
			verify(writer, never()).approve(any(), any(), any(), any());
		}

		@Test
		@DisplayName("토스 결과가 불명이면 결제를 UNKNOWN 으로 기록하고 같은 예외를 다시 던진다")
		void marksUnknownAndRethrowsWhenClientResultIsUnknown() {
			// given
			given(writer.prepare(MEMBER_ID, request)).willReturn(preparation);
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS 통신 실패: Read timed out");
			willThrow(resultUnknown).given(paymentClient).confirm(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT);

			// when & then
			assertThatThrownBy(() -> service.confirm(MEMBER_ID, request))
					.isSameAs(resultUnknown);
			verify(writer).markUnknown(PAYMENT_ID, resultUnknown.getMessage());
			verify(writer, never()).fail(any(), any());
			verify(writer, never()).approve(any(), any(), any(), any());
		}

		@Test
		@DisplayName("승인 반영이 비즈니스 예외가 아닌 예외로 실패하면 결제를 UNKNOWN 으로 기록하고 결과 불명 예외를 던진다")
		void marksUnknownAndThrowsResultUnknownWhenApproveThrowsUnexpectedException() {
			// given
			given(writer.prepare(MEMBER_ID, request)).willReturn(preparation);
			PaymentConfirmResult result = new PaymentConfirmResult(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER,
					PaymentFixture.METHOD, AMOUNT, LocalDateTime.now());
			given(paymentClient.confirm(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT)).willReturn(result);
			willThrow(new CannotAcquireLockException("lock timeout"))
					.given(writer).approve(ORDER_ID, PAYMENT_ID, PaymentFixture.PAYMENT_KEY, result);

			// when & then
			assertThatThrownBy(() -> service.confirm(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
			verify(writer).markUnknown(eq(PAYMENT_ID), anyString());
			verify(writer, never()).fail(any(), any());
		}

		@Test
		@DisplayName("승인 반영 실패에 이어 결과 불명 기록마저 실패해도 결과 불명 예외를 던진다")
		void throwsResultUnknownEvenWhenMarkUnknownAlsoFailsAfterApproveFailure() {
			// given
			given(writer.prepare(MEMBER_ID, request)).willReturn(preparation);
			PaymentConfirmResult result = new PaymentConfirmResult(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER,
					PaymentFixture.METHOD, AMOUNT, LocalDateTime.now());
			given(paymentClient.confirm(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT)).willReturn(result);
			willThrow(new CannotAcquireLockException("lock timeout"))
					.given(writer).approve(ORDER_ID, PAYMENT_ID, PaymentFixture.PAYMENT_KEY, result);
			willThrow(new CannotAcquireLockException("db down"))
					.given(writer).markUnknown(eq(PAYMENT_ID), anyString());

			// when & then
			assertThatThrownBy(() -> service.confirm(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}

		@Test
		@DisplayName("실패 기록 중 승인이 먼저 커밋돼 버전이 충돌해도 원래 거절 예외가 그대로 전파된다")
		void swallowsVersionConflictOnFailAndRethrowsOriginalRejection() {
			// given
			given(writer.prepare(MEMBER_ID, request)).willReturn(preparation);
			BusinessException confirmFailed = new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED,
					"TOSS REJECT_CARD_COMPANY: 카드사에서 승인을 거절했습니다.");
			willThrow(confirmFailed).given(paymentClient).confirm(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT);
			willThrow(versionConflict()).given(writer).fail(PAYMENT_ID, confirmFailed.getMessage());

			// when & then
			assertThatThrownBy(() -> service.confirm(MEMBER_ID, request))
					.isSameAs(confirmFailed);
		}

		@Test
		@DisplayName("결과 불명 기록 중 승인이 먼저 커밋돼 버전이 충돌해도 원래 결과 불명 예외가 그대로 전파된다")
		void swallowsVersionConflictOnMarkUnknownAndRethrowsOriginalException() {
			// given
			given(writer.prepare(MEMBER_ID, request)).willReturn(preparation);
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS 통신 실패: Read timed out");
			willThrow(resultUnknown).given(paymentClient).confirm(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT);
			willThrow(versionConflict()).given(writer).markUnknown(PAYMENT_ID, resultUnknown.getMessage());

			// when & then
			assertThatThrownBy(() -> service.confirm(MEMBER_ID, request))
					.isSameAs(resultUnknown);
		}
	}

	@Nested
	@DisplayName("confirm() 승인 후 approve() 가 거절되면")
	class ApproveRejectedAfterTossApproval {

		private PaymentConfirmResult stubTossApproval() {
			PaymentConfirmResult result = new PaymentConfirmResult(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER,
					PaymentFixture.METHOD, AMOUNT, PaymentFixture.APPROVED_AT);
			given(writer.prepare(MEMBER_ID, request)).willReturn(preparation);
			given(paymentClient.confirm(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT)).willReturn(result);
			return result;
		}

		@Test
		@DisplayName("주문이 취소·만료돼 ORDER_INVALID_STATUS 면 보상 취소하고 성공 시 ORDER_EXPIRED 를 던진다")
		void compensatesAndThrowsOrderExpiredWhenOrderInvalidatedAndCompensationSucceeds() {
			// given
			PaymentConfirmResult result = stubTossApproval();
			BusinessException orderInvalidStatus = new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
			willThrow(orderInvalidStatus).given(writer)
					.approve(ORDER_ID, PAYMENT_ID, PaymentFixture.PAYMENT_KEY, result);
			given(compensator.cancelApproved(PAYMENT_ID, PaymentFixture.PAYMENT_KEY, result.approvedAt(),
					PaymentCompensator.ORDER_INVALIDATED_REASON))
					.willReturn(CompensationResult.canceled(PaymentFixture.CANCELED_AT));

			// when & then
			assertThatThrownBy(() -> service.confirm(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_EXPIRED);
			verify(writer, never()).fail(any(), any());
		}

		@Test
		@DisplayName("주문 무효 보상 취소마저 실패하면 PAYMENT_RESULT_UNKNOWN 을 던진다")
		void throwsPaymentResultUnknownWhenOrderInvalidatedCompensationFails() {
			// given
			PaymentConfirmResult result = stubTossApproval();
			BusinessException orderInvalidStatus = new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
			willThrow(orderInvalidStatus).given(writer)
					.approve(ORDER_ID, PAYMENT_ID, PaymentFixture.PAYMENT_KEY, result);
			given(compensator.cancelApproved(PAYMENT_ID, PaymentFixture.PAYMENT_KEY, result.approvedAt(),
					PaymentCompensator.ORDER_INVALIDATED_REASON))
					.willReturn(CompensationResult.notCanceled("TOSS ALREADY_CANCELED_PAYMENT"));

			// when & then
			assertThatThrownBy(() -> service.confirm(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
			verify(writer, never()).fail(any(), any());
		}

		@ParameterizedTest
		@EnumSource(value = ErrorCode.class,
				names = {"PAYMENT_ALREADY_DONE", "ORDER_ALREADY_PAID", "PAYMENT_INVALID_STATUS"})
		@DisplayName("같은 주문이 다른 키로 먼저 승인됐으면 DB 행은 건드리지 않고 토스만 보상 취소한 뒤 원래 예외를 던진다")
		void compensatesWithoutTouchingPaymentRowAndRethrowsOriginalException(ErrorCode errorCode) {
			// given
			PaymentConfirmResult result = stubTossApproval();
			BusinessException duplicateApproval = new BusinessException(errorCode);
			willThrow(duplicateApproval).given(writer)
					.approve(ORDER_ID, PAYMENT_ID, PaymentFixture.PAYMENT_KEY, result);

			// when & then
			assertThatThrownBy(() -> service.confirm(MEMBER_ID, request))
					.isSameAs(duplicateApproval);
			verify(compensator).cancelApproved(isNull(), eq(PaymentFixture.PAYMENT_KEY), eq(result.approvedAt()),
					eq(PaymentCompensator.DUPLICATE_APPROVAL_REASON));
			verify(writer, never()).fail(any(), any());
			verify(writer, never()).markUnknown(any(), any());
		}

		@Test
		@DisplayName("자동 보상 대상이 아닌 예외면 결제를 UNKNOWN 으로 남기고 원래 예외를 던진다")
		void marksUnknownWithoutCompensatingAndRethrowsOriginalExceptionForNonCompensableCodes() {
			// given
			PaymentConfirmResult result = stubTossApproval();
			BusinessException keyMismatch = new BusinessException(ErrorCode.PAYMENT_KEY_MISMATCH);
			willThrow(keyMismatch).given(writer).approve(ORDER_ID, PAYMENT_ID, PaymentFixture.PAYMENT_KEY, result);

			// when & then
			assertThatThrownBy(() -> service.confirm(MEMBER_ID, request))
					.isSameAs(keyMismatch);
			verify(writer).markUnknown(PAYMENT_ID, keyMismatch.getMessage());
			verify(writer, never()).fail(any(), any());
			verify(compensator, never()).cancelApproved(any(), any(), any(), any());
		}
	}
}
