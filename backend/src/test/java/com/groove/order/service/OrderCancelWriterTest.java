package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderCancelWriterTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ORDER_ID = 10L;

	@Mock
	OrderRepository orderRepository;

	@Mock
	PaymentRepository paymentRepository;

	@Mock
	OrderCancelRestorer restorer;

	OrderCancelWriter writer;
	Order order;

	@BeforeEach
	void setUp() {
		writer = new OrderCancelWriter(orderRepository, paymentRepository, restorer);
		Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 100L);
		order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
	}

	@Nested
	@DisplayName("findTarget()")
	class FindTarget {

		@Test
		@DisplayName("소유 주문과 결제 상태를 반환한다")
		void returnsOwnedOrderAndPaymentStatus() {
			// given
			Payment payment = PaymentFixture.approved(order);
			given(orderRepository.findByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

			// when
			OrderCancelTarget result = writer.findTarget(MEMBER_ID, ORDER_ID);

			// then
			assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
			assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(result.requiresPaymentCancel()).isTrue();
		}

		@Test
		@DisplayName("소유 주문이 없으면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderNotFound() {
			// given
			given(orderRepository.findByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.findTarget(MEMBER_ID, ORDER_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("cancelUnpaid()")
	class CancelUnpaid {

		@Test
		@DisplayName("미결제 주문이면 취소하고 자원을 복구한다")
		void cancelsAndRestoresUnpaidOrder() {
			// given
			LimitedRelease release = new LimitedRelease(30L, MEMBER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
			given(restorer.restore(order, false)).willReturn(Optional.of(release));

			// when
			UnpaidCancelResult result = writer.cancelUnpaid(MEMBER_ID, ORDER_ID, "고객 변심");

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(result.limitedDropId()).isEqualTo(30L);
			verify(restorer).restore(order, false);
		}

		@Test
		@DisplayName("락 대기 중 결제가 승인됐으면 주문을 바꾸지 않고 결제 취소 필요를 반환한다")
		void returnsPaymentCancelRequiredWhenApprovedAfterLookup() {
			// given
			Payment payment = PaymentFixture.approved(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

			// when
			UnpaidCancelResult result = writer.cancelUnpaid(MEMBER_ID, ORDER_ID, "고객 변심");

			// then
			assertThat(result.needsPaymentCancel()).isTrue();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
			verify(restorer, never()).restore(order, false);
		}

		@Test
		@DisplayName("다른 회원의 주문이면 ORDER_NOT_FOUND 예외를 던진다")
		void rejectsOtherMembersOrder() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.cancelUnpaid(999L, ORDER_ID, "고객 변심"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}
	}
}
