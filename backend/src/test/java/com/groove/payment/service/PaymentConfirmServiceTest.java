package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.PaymentFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.dto.PaymentConfirmResponse;
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

	PaymentConfirmService service;

	PaymentConfirmRequest request;

	@BeforeEach
	void setUp() {
		service = new PaymentConfirmService(writer, paymentClient);
		request = new PaymentConfirmRequest(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT.longValueExact());
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
			ConfirmPreparation preparation = new ConfirmPreparation(PAYMENT_ID, ORDER_ID, ORDER_NUMBER, AMOUNT,
					Optional.of(alreadyApproved));
			given(writer.prepare(MEMBER_ID, request)).willReturn(preparation);

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
			ConfirmPreparation preparation = new ConfirmPreparation(PAYMENT_ID, ORDER_ID, ORDER_NUMBER, AMOUNT,
					Optional.empty());
			given(writer.prepare(MEMBER_ID, request)).willReturn(preparation);
			PaymentConfirmResult result = new PaymentConfirmResult(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER,
					PaymentFixture.METHOD, AMOUNT, LocalDateTime.now());
			given(paymentClient.confirm(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT)).willReturn(result);
			PaymentConfirmResponse approvedResponse = new PaymentConfirmResponse(PAYMENT_ID, ORDER_ID, ORDER_NUMBER,
					PaymentStatus.DONE, PaymentFixture.METHOD, AMOUNT, PaymentFixture.APPROVED_AT);
			given(writer.approve(eq(PAYMENT_ID), eq(PaymentFixture.PAYMENT_KEY), eq(result)))
					.willReturn(approvedResponse);

			// when
			PaymentConfirmResponse response = service.confirm(MEMBER_ID, request);

			// then
			assertThat(response).isEqualTo(approvedResponse);
			verify(writer, never()).fail(any(), any());
		}

		@Test
		@DisplayName("토스 승인이 실패하면 실패 사유를 저장하고 같은 예외를 다시 던진다")
		void marksFailureAndRethrowsWhenClientFails() {
			// given
			ConfirmPreparation preparation = new ConfirmPreparation(PAYMENT_ID, ORDER_ID, ORDER_NUMBER, AMOUNT,
					Optional.empty());
			given(writer.prepare(MEMBER_ID, request)).willReturn(preparation);
			BusinessException confirmFailed = new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED,
					"TOSS REJECT_CARD_COMPANY: 카드사에서 승인을 거절했습니다.");
			willThrow(confirmFailed).given(paymentClient).confirm(PaymentFixture.PAYMENT_KEY, ORDER_NUMBER, AMOUNT);

			// when & then
			assertThatThrownBy(() -> service.confirm(MEMBER_ID, request))
					.isSameAs(confirmFailed);
			verify(writer).fail(PAYMENT_ID, confirmFailed.getMessage());
		}
	}
}
