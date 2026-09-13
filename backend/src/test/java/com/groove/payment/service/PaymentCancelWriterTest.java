package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
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
class PaymentCancelWriterTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ORDER_ID = 10L;
	private static final Long PAYMENT_ID = 20L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 12, 0);
	private static final LocalDateTime TOSS_CANCELED_AT = LocalDateTime.of(2026, 9, 13, 11, 59);

	@Mock
	OrderRepository orderRepository;

	@Mock
	PaymentRepository paymentRepository;

	@Mock
	OrderCancelRestorer restorer;

	PaymentCancelWriter writer;
	Order order;
	Payment payment;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-13T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		writer = new PaymentCancelWriter(orderRepository, paymentRepository, restorer, clock);
		Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 100L);
		order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
		order.markPaid();
		payment = PaymentFixture.approved(order);
		ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
	}

	@Nested
	@DisplayName("requestCancel()")
	class RequestCancel {

		@Test
		@DisplayName("주문 락 뒤 주문과 결제를 취소 요청 상태로 바꾼다")
		void requestsCancelAfterLockingOrder() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

			// when
			CancelRequest result = writer.requestCancel(ORDER_ID, MEMBER_ID, "고객 변심");

			// then
			InOrder inOrder = Mockito.inOrder(orderRepository, paymentRepository);
			inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
			inOrder.verify(paymentRepository).findByOrderId(ORDER_ID);
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
			assertThat(order.getCancelReason()).isEqualTo("고객 변심");
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
			assertThat(result.alreadyRequested()).isFalse();
			assertThat(result.tossReason()).isEqualTo("고객 변심");
		}

		@Test
		@DisplayName("이미 CANCEL_REQUESTED 면 상태를 바꾸지 않고 중복 요청으로 반환한다")
		void returnsDuplicateWhenAlreadyRequested() {
			// given
			order.requestCancel("기존 사유", false);
			payment.requestCancel();
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

			// when
			CancelRequest result = writer.requestCancel(ORDER_ID, MEMBER_ID, "새 사유");

			// then
			assertThat(result.alreadyRequested()).isTrue();
			assertThat(result.tossReason()).isEqualTo("기존 사유");
		}

		@Test
		@DisplayName("회원 소유 주문이 아니면 ORDER_NOT_FOUND 예외를 던진다")
		void rejectsOtherMembersOrder() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.requestCancel(ORDER_ID, 999L, "고객 변심"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("회원 사유가 없으면 토스 기본 사유를 반환한다")
		void usesDefaultTossReasonWhenMemberReasonIsMissing() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

			// when
			CancelRequest result = writer.requestCancel(ORDER_ID, MEMBER_ID, null);

			// then
			assertThat(result.tossReason()).isEqualTo("주문 취소");
		}

		@Test
		@DisplayName("DONE 이 아닌 결제면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void rejectsInvalidPaymentStatus() {
			// given
			Payment ready = Payment.ready(order);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(ready));

			// when & then
			assertThatThrownBy(() -> writer.requestCancel(ORDER_ID, MEMBER_ID, "고객 변심"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}

		@Test
		@DisplayName("결제가 없으면 주문을 변경하지 않고 PAYMENT_NOT_FOUND 예외를 던진다")
		void doesNotChangeOrderWhenPaymentNotFound() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.requestCancel(ORDER_ID, MEMBER_ID, "고객 변심"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
			assertThat(order.getCancelReason()).isNull();
		}

		@Test
		@DisplayName("관리자 취소면 회원 소유 확인 없이 관리자 사유를 기록한다")
		void requestsAdminCancelWithoutMemberOwnershipCheck() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

			// when
			CancelRequest result = writer.requestCancel(ORDER_ID, null, null);

			// then
			assertThat(result.tossReason()).isEqualTo("관리자 취소");
		}
	}

	@Nested
	@DisplayName("completeCancel()")
	class CompleteCancel {

		@Test
		@DisplayName("주문 락 뒤 주문 복구와 결제 취소를 완료한다")
		void completesOrderAndPaymentCancel() {
			// given
			order.requestCancel("고객 변심", false);
			payment.requestCancel();
			LimitedRelease release = new LimitedRelease(30L, MEMBER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(restorer.restore(order, true)).willReturn(Optional.of(release));

			// when
			Optional<LimitedRelease> result = writer.completeCancel(ORDER_ID, PAYMENT_ID, TOSS_CANCELED_AT);

			// then
			verify(orderRepository).findByIdForUpdate(ORDER_ID);
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCanceledAt()).isEqualTo(NOW);
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(payment.getCanceledAt()).isEqualTo(TOSS_CANCELED_AT);
			assertThat(result).contains(release);
		}

		@Test
		@DisplayName("이미 CANCELED 면 복구를 반복하지 않는다")
		void doesNothingWhenAlreadyCanceled() {
			// given
			Payment canceled = PaymentFixture.canceled(order);
			ReflectionTestUtils.setField(canceled, "id", PAYMENT_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(canceled));

			// when
			Optional<LimitedRelease> result = writer.completeCancel(ORDER_ID, PAYMENT_ID, TOSS_CANCELED_AT);

			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("CANCEL_REQUESTED 가 아니면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void rejectsInvalidPaymentStatus() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when & then
			assertThatThrownBy(() -> writer.completeCancel(ORDER_ID, PAYMENT_ID, TOSS_CANCELED_AT))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("revertCancelRequest()")
	class RevertCancelRequest {

		@Test
		@DisplayName("CANCEL_REQUESTED 면 결제를 DONE 으로 되돌리고 주문 사유를 지운다")
		void revertsPaymentAndOrderRequest() {
			// given
			order.requestCancel("고객 변심", false);
			payment.requestCancel();
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			writer.revertCancelRequest(ORDER_ID, PAYMENT_ID);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(order.getCancelReason()).isNull();
		}

		@Test
		@DisplayName("이미 요청 상태가 아니면 아무것도 바꾸지 않는다")
		void doesNothingWhenNotRequested() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			writer.revertCancelRequest(ORDER_ID, PAYMENT_ID);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
		}
	}
}
