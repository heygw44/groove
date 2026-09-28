package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.service.LimitedRelease;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderCancelRestorer;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class PendingVirtualAccountCancelWriterTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ORDER_ID = 10L;
	private static final Long PAYMENT_ID = 20L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 12, 0);

	@Mock
	OrderRepository orderRepository;

	@Mock
	PaymentRepository paymentRepository;

	@Mock
	OrderCancelRestorer restorer;

	PendingVirtualAccountCancelWriter writer;
	Order order;
	Payment payment;

	@BeforeEach
	void setUp() {
		writer = new PendingVirtualAccountCancelWriter(orderRepository, paymentRepository, restorer);
		Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 100L);
		order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
		payment = Payment.ready(order);
		payment.issueVirtualAccount(PaymentFixture.PAYMENT_KEY, "가상계좌", "088", "12345678901234", "홍길동",
				NOW.plusHours(24), "hash");
		ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
	}

	@Nested
	@DisplayName("lock()")
	class Lock {

		@Test
		@DisplayName("WAITING_FOR_DEPOSIT 면 paymentKey 를 담아 반환한다")
		void returnsTargetWhenWaitingForDeposit() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

			// when
			PendingVirtualAccountCancelTarget target = writer.lock(ORDER_ID, MEMBER_ID);

			// then
			assertThat(target.paymentId()).isEqualTo(PAYMENT_ID);
			assertThat(target.paymentKey()).isEqualTo(PaymentFixture.PAYMENT_KEY);
		}

		@Test
		@DisplayName("회원 소유 주문이 아니면 ORDER_NOT_FOUND 예외를 던진다")
		void rejectsOtherMembersOrder() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.lock(ORDER_ID, 999L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("결제가 없으면 PAYMENT_NOT_FOUND 예외를 던진다")
		void throwsWhenPaymentNotFound() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.lock(ORDER_ID, MEMBER_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
		}

		@Test
		@DisplayName("WAITING_FOR_DEPOSIT 가 아니면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsWhenNotWaitingForDeposit() {
			// given
			Payment done = PaymentFixture.approved(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(done));

			// when & then
			assertThatThrownBy(() -> writer.lock(ORDER_ID, MEMBER_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("finalizeCancel()")
	class FinalizeCancel {

		@Test
		@DisplayName("여전히 WAITING_FOR_DEPOSIT 면 결제를 취소하고 주문을 취소·복원한다")
		void cancelsPaymentAndOrder() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(restorer.restore(order, false)).willReturn(Optional.of(new LimitedRelease(30L, MEMBER_ID)));

			// when
			Optional<LimitedRelease> result = writer.finalizeCancel(ORDER_ID, PAYMENT_ID, "주문 취소", NOW);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(result).contains(new LimitedRelease(30L, MEMBER_ID));
		}

		@Test
		@DisplayName("그 사이 입금이 확인돼 더 이상 WAITING_FOR_DEPOSIT 이 아니면 아무것도 하지 않는다")
		void doesNothingWhenAlreadyResolved() {
			// given
			Payment done = PaymentFixture.approved(order);
			ReflectionTestUtils.setField(done, "id", PAYMENT_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(done));

			// when
			Optional<LimitedRelease> result = writer.finalizeCancel(ORDER_ID, PAYMENT_ID, "주문 취소", NOW);

			// then
			assertThat(result).isEmpty();
			verify(restorer, never()).restore(any(), any(Boolean.class));
		}
	}
}
