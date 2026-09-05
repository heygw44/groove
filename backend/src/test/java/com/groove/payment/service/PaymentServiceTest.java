package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderDetailResponse;
import com.groove.order.entity.Order;
import com.groove.order.service.OrderService;
import com.groove.payment.dto.PaymentCancelRequest;
import com.groove.payment.dto.PaymentCancelResponse;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long PAYMENT_ID = 10L;

	@Mock
	PaymentRepository paymentRepository;

	@Mock
	OrderService orderService;

	PaymentService service;

	Member member;

	@BeforeEach
	void setUp() {
		service = new PaymentService(paymentRepository, orderService);
		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("본인 결제가 아니거나 없으면 PAYMENT_NOT_FOUND 예외를 던지고 주문 취소를 호출하지 않는다")
		void throwsWhenPaymentNotFoundOrNotOwned() {
			// given
			given(paymentRepository.findByIdAndOrderMemberId(PAYMENT_ID, MEMBER_ID)).willReturn(Optional.empty());
			PaymentCancelRequest request = new PaymentCancelRequest("고객 변심");

			// when & then
			assertThatThrownBy(() -> service.cancel(MEMBER_ID, PAYMENT_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
			verify(orderService, never()).cancel(any(), any(), any());
		}

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"READY", "FAILED", "CANCELED"})
		@DisplayName("DONE 이 아닌 결제면 PAYMENT_INVALID_STATUS 예외를 던지고 주문 취소를 호출하지 않는다")
		void throwsWhenPaymentIsNotDone(PaymentStatus status) {
			// given
			Order order = OrderFixture.withId(OrderFixture.create(member), 100L);
			Payment payment = PaymentFixture.withStatus(PaymentFixture.approved(order), status);
			given(paymentRepository.findByIdAndOrderMemberId(PAYMENT_ID, MEMBER_ID)).willReturn(Optional.of(payment));
			PaymentCancelRequest request = new PaymentCancelRequest("고객 변심");

			// when & then
			assertThatThrownBy(() -> service.cancel(MEMBER_ID, PAYMENT_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
			verify(orderService, never()).cancel(any(), any(), any());
		}

		@Test
		@DisplayName("DONE 결제면 주문 취소에 위임하고 갱신된 결제 정보를 반환한다")
		void delegatesToOrderServiceWhenDone() {
			// given
			Order order = OrderFixture.withId(OrderFixture.create(member), 100L);
			Payment payment = PaymentFixture.approved(order);
			given(paymentRepository.findByIdAndOrderMemberId(PAYMENT_ID, MEMBER_ID)).willReturn(Optional.of(payment));
			given(orderService.cancel(any(), any(), any()))
					.willReturn(OrderDetailResponse.from(order, null));
			PaymentCancelRequest request = new PaymentCancelRequest("고객 변심");

			// when
			PaymentCancelResponse response = service.cancel(MEMBER_ID, PAYMENT_ID, request);

			// then
			ArgumentCaptor<OrderCancelRequest> captor = ArgumentCaptor.forClass(OrderCancelRequest.class);
			verify(orderService).cancel(eq(MEMBER_ID), eq(order.getId()), captor.capture());
			assertThat(captor.getValue().reason()).isEqualTo("고객 변심");
			assertThat(response.paymentId()).isEqualTo(payment.getId());
			assertThat(response.orderId()).isEqualTo(order.getId());
		}
	}
}
