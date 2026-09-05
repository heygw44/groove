package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentCancelServiceTest {

	private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 9, 5, 10, 0, 0);

	@Mock
	PaymentRepository paymentRepository;

	@Mock
	PaymentClient paymentClient;

	Clock clock;

	PaymentCancelService service;

	Member member;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(FIXED_NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
		service = new PaymentCancelService(paymentRepository, paymentClient, clock);
		member = MemberFixture.withId(MemberFixture.create(), 1L);
	}

	@Nested
	@DisplayName("onPaidOrderCanceled()")
	class OnPaidOrderCanceled {

		@Test
		@DisplayName("DONE 결제면 토스 취소를 호출하고 CANCELED + 취소 시각으로 반영한다")
		void cancelsPaymentWhenDone() {
			// given
			Order order = OrderFixture.withId(OrderFixture.create(member), 1L);
			OrderFixture.markPaid(order);
			order.cancel("고객 변심");
			Payment payment = PaymentFixture.approved(order);
			given(paymentRepository.findByOrderId(order.getId())).willReturn(Optional.of(payment));
			LocalDateTime tossCanceledAt = LocalDateTime.of(2026, 9, 5, 10, 0, 5);
			given(paymentClient.cancel(payment.getPaymentKey(), "고객 변심"))
					.willReturn(new PaymentCancelResult(payment.getPaymentKey(), "CANCELED", tossCanceledAt));

			// when
			Long canceledPaymentId = service.onPaidOrderCanceled(order);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(payment.getCanceledAt()).isEqualTo(tossCanceledAt);
			assertThat(canceledPaymentId).isEqualTo(payment.getId());
		}

		@Test
		@DisplayName("주문 취소 사유가 비어 있으면 기본 문구로 토스를 호출한다")
		void usesDefaultReasonWhenOrderReasonIsBlank() {
			// given
			Order order = OrderFixture.withId(OrderFixture.create(member), 1L);
			OrderFixture.markPaid(order);
			order.cancel(null);
			Payment payment = PaymentFixture.approved(order);
			given(paymentRepository.findByOrderId(order.getId())).willReturn(Optional.of(payment));
			given(paymentClient.cancel(eq(payment.getPaymentKey()), eq("주문 취소")))
					.willReturn(new PaymentCancelResult(payment.getPaymentKey(), "CANCELED", null));

			// when
			service.onPaidOrderCanceled(order);

			// then
			verify(paymentClient).cancel(payment.getPaymentKey(), "주문 취소");
		}

		@Test
		@DisplayName("토스 취소 결과에 취소 시각이 없으면 서버 시각을 쓴다")
		void usesServerTimeWhenTossCanceledAtIsNull() {
			// given
			Order order = OrderFixture.withId(OrderFixture.create(member), 1L);
			OrderFixture.markPaid(order);
			order.cancel("고객 변심");
			Payment payment = PaymentFixture.approved(order);
			given(paymentRepository.findByOrderId(order.getId())).willReturn(Optional.of(payment));
			given(paymentClient.cancel(any(), any()))
					.willReturn(new PaymentCancelResult(payment.getPaymentKey(), "CANCELED", null));

			// when
			service.onPaidOrderCanceled(order);

			// then
			assertThat(payment.getCanceledAt()).isEqualTo(LocalDateTime.now(clock));
		}

		@Test
		@DisplayName("결제가 없으면 PAYMENT_NOT_FOUND 예외를 던지고 토스를 호출하지 않는다")
		void throwsWhenPaymentNotFound() {
			// given
			Order order = OrderFixture.withId(OrderFixture.create(member), 1L);
			OrderFixture.markPaid(order);
			order.cancel("고객 변심");
			given(paymentRepository.findByOrderId(order.getId())).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> service.onPaidOrderCanceled(order))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
			verify(paymentClient, never()).cancel(any(), any());
		}

		@Test
		@DisplayName("이미 취소된 결제면 PAYMENT_INVALID_STATUS 예외를 던지고 토스를 호출하지 않는다")
		void throwsWhenAlreadyCanceled() {
			// given
			Order order = OrderFixture.withId(OrderFixture.create(member), 1L);
			OrderFixture.markPaid(order);
			order.cancel("고객 변심");
			Payment payment = PaymentFixture.canceled(order);
			given(paymentRepository.findByOrderId(order.getId())).willReturn(Optional.of(payment));

			// when & then
			assertThatThrownBy(() -> service.onPaidOrderCanceled(order))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
			verify(paymentClient, never()).cancel(any(), any());
		}

		@Test
		@DisplayName("토스 취소가 실패하면 예외가 그대로 전파되고 결제는 DONE 으로 유지된다")
		void propagatesExceptionAndKeepsPaymentDoneWhenClientFails() {
			// given
			Order order = OrderFixture.withId(OrderFixture.create(member), 1L);
			OrderFixture.markPaid(order);
			order.cancel("고객 변심");
			Payment payment = PaymentFixture.approved(order);
			given(paymentRepository.findByOrderId(order.getId())).willReturn(Optional.of(payment));
			BusinessException cancelFailed = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED,
					"TOSS ALREADY_CANCELED_PAYMENT");
			willThrow(cancelFailed).given(paymentClient).cancel(any(), any());

			// when & then
			assertThatThrownBy(() -> service.onPaidOrderCanceled(order)).isSameAs(cancelFailed);
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
		}
	}
}
