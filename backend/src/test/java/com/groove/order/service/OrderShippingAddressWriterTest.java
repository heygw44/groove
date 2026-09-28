package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderSource;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.Payment;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderShippingAddressWriterTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ORDER_ID = 500L;
	private static final Long ADDRESS_ID = 78L;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private AddressRepository addressRepository;

	@Mock
	private PaymentRepository paymentRepository;

	private OrderShippingAddressWriter writer;

	private Member member;
	private Product product;
	private Address address;

	@BeforeEach
	void setUp() {
		writer = new OrderShippingAddressWriter(orderRepository, addressRepository, paymentRepository);

		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
		// 주문 생성 시 기본 배송지(OrderFixture)와 구분되도록 수령인을 다르게 둔다.
		address = AddressFixture.withId(AddressFixture.create(member, "김바이닐"), ADDRESS_ID);
	}

	@Nested
	@DisplayName("changeAddress()")
	class ChangeAddress {

		@Test
		@DisplayName("PENDING 이고 결제 대기가 아니면 배송지를 바꾼다")
		void changesAddressWhenEligible() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));

			// when
			writer.changeAddress(MEMBER_ID, ORDER_ID, ADDRESS_ID);

			// then
			assertThat(order.getShippingAddress().getRecipientName()).isEqualTo(address.getRecipientName());
		}

		@Test
		@DisplayName("한정반 주문이어도 PENDING 이면 배송지를 바꾼다")
		void changesAddressForLimitedOrder() {
			// given
			Order order = OrderFixture.withId(OrderFixture.create(member, "20260903-LTD001", OrderSource.LIMITED),
					ORDER_ID);
			order.addItem(product, 1);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));

			// when
			writer.changeAddress(MEMBER_ID, ORDER_ID, ADDRESS_ID);

			// then
			assertThat(order.getShippingAddress().getRecipientName()).isEqualTo(address.getRecipientName());
		}

		@Test
		@DisplayName("주문이 없거나 본인 소유가 아니면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderNotOwned() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.changeAddress(MEMBER_ID, ORDER_ID, ADDRESS_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("이미 확정된 주문이면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsWhenAlreadyPlaced() {
			// given
			Order order = OrderFixture.withId(OrderFixture.place(OrderFixture.createWithItem(member, product, 1)),
					ORDER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.changeAddress(MEMBER_ID, ORDER_ID, ADDRESS_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}

		@Test
		@DisplayName("PENDING 이 아니면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsWhenNotPending() {
			// given
			Order order = OrderFixture.withId(OrderFixture.markPaid(OrderFixture.createWithItem(member, product, 1)),
					ORDER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.changeAddress(MEMBER_ID, ORDER_ID, ADDRESS_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}

		@Test
		@DisplayName("결제가 READY 면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsWhenPaymentReady() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
			Payment readyPayment = Payment.ready(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(readyPayment));

			// when & then
			assertThatThrownBy(() -> writer.changeAddress(MEMBER_ID, ORDER_ID, ADDRESS_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}

		@Test
		@DisplayName("결제가 UNKNOWN 이면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsWhenPaymentUnknown() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
			Payment unknownPayment = PaymentFixture.unknown(order, "확인 중");
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(unknownPayment));

			// when & then
			assertThatThrownBy(() -> writer.changeAddress(MEMBER_ID, ORDER_ID, ADDRESS_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}

		@Test
		@DisplayName("결제가 FAILED 면 배송지를 바꾼다")
		void changesAddressWhenPaymentFailed() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
			Payment failedPayment = PaymentFixture.failed(order, "카드 한도 초과");
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(failedPayment));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));

			// when
			writer.changeAddress(MEMBER_ID, ORDER_ID, ADDRESS_ID);

			// then
			assertThat(order.getShippingAddress().getRecipientName()).isEqualTo(address.getRecipientName());
		}

		@Test
		@DisplayName("본인 소유 배송지가 아니면 MEMBER_ADDRESS_NOT_FOUND 예외를 던진다")
		void throwsWhenAddressNotOwned() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.changeAddress(MEMBER_ID, ORDER_ID, ADDRESS_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_ADDRESS_NOT_FOUND);
		}
	}
}
