package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderDetailResponse;
import com.groove.order.dto.OrderPaymentResponse;
import com.groove.order.entity.OrderStatus;
import com.groove.order.service.OrderCancelService;
import com.groove.payment.dto.PaymentCancelRequest;
import com.groove.payment.dto.PaymentCancelResponse;
import com.groove.payment.dto.PaymentCancelTarget;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long PAYMENT_ID = 10L;
	private static final Long ORDER_ID = 100L;

	@Mock
	PaymentRepository paymentRepository;

	@Mock
	OrderCancelService orderCancelService;

	PaymentService service;

	@BeforeEach
	void setUp() {
		service = new PaymentService(paymentRepository, orderCancelService);
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("본인 결제가 아니거나 없으면 PAYMENT_NOT_FOUND 예외를 던진다")
		void throwsWhenPaymentNotFoundOrNotOwned() {
			// given
			given(paymentRepository.findCancelTarget(PAYMENT_ID, MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> service.cancel(MEMBER_ID, PAYMENT_ID, new PaymentCancelRequest("고객 변심")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
			verify(orderCancelService, never()).cancel(any(), any(), any());
		}

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"READY", "FAILED", "CANCELED", "UNKNOWN"})
		@DisplayName("DONE 또는 CANCEL_REQUESTED 가 아니면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsWhenPaymentIsNotCancelable(PaymentStatus status) {
			// given
			given(paymentRepository.findCancelTarget(PAYMENT_ID, MEMBER_ID))
					.willReturn(Optional.of(new PaymentCancelTarget(ORDER_ID, status)));

			// when & then
			assertThatThrownBy(() -> service.cancel(MEMBER_ID, PAYMENT_ID, new PaymentCancelRequest("고객 변심")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"DONE", "CANCEL_REQUESTED"})
		@DisplayName("취소 가능한 결제면 주문 취소에 위임하고 응답을 변환한다")
		void delegatesToOrderCancelService(PaymentStatus status) {
			// given
			given(paymentRepository.findCancelTarget(PAYMENT_ID, MEMBER_ID))
					.willReturn(Optional.of(new PaymentCancelTarget(ORDER_ID, status)));
			OrderPaymentResponse payment = new OrderPaymentResponse(PAYMENT_ID, "카드", status,
					new BigDecimal("30000"), LocalDateTime.of(2026, 9, 13, 10, 0), null);
			OrderDetailResponse detail = new OrderDetailResponse(ORDER_ID, "20260913-TESTAB12", OrderStatus.PAID,
					new BigDecimal("30000"), BigDecimal.ZERO, new BigDecimal("30000"), null, List.of(), null,
					null, null, null, "고객 변심", null, payment);
			given(orderCancelService.cancel(eq(MEMBER_ID), eq(ORDER_ID), any())).willReturn(detail);

			// when
			PaymentCancelResponse response = service.cancel(MEMBER_ID, PAYMENT_ID,
					new PaymentCancelRequest("고객 변심"));

			// then
			ArgumentCaptor<OrderCancelRequest> request = ArgumentCaptor.forClass(OrderCancelRequest.class);
			verify(orderCancelService).cancel(eq(MEMBER_ID), eq(ORDER_ID), request.capture());
			assertThat(request.getValue().reason()).isEqualTo("고객 변심");
			assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
			assertThat(response.status()).isEqualTo(status);
		}
	}
}
