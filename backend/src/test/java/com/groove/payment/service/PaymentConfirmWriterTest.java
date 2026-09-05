package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.dto.PaymentConfirmResponse;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmWriterTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ORDER_ID = 500L;
	private static final BigDecimal PRICE = new BigDecimal("45000");

	@Mock
	OrderRepository orderRepository;

	@Mock
	PaymentRepository paymentRepository;

	PaymentConfirmWriter writer;

	Member member;
	Order order;
	Clock clock;
	LocalDateTime now;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		writer = new PaymentConfirmWriter(orderRepository, paymentRepository, clock);

		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 100L);
		order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
	}

	private PaymentConfirmRequest requestOf(String paymentKey, long amount) {
		return new PaymentConfirmRequest(paymentKey, order.getOrderNumber(), amount);
	}

	private Payment paymentWithId(Payment payment, Long id) {
		ReflectionTestUtils.setField(payment, "id", id);
		return payment;
	}

	@Nested
	@DisplayName("prepare()")
	class Prepare {

		@Test
		@DisplayName("정상 요청이면 결제 레코드를 새로 만들고 승인 전 상태로 반환한다")
		void createsPaymentAndReturnsPreparation() {
			// given
			given(orderRepository.findByOrderNumberForUpdate(order.getOrderNumber())).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
			given(paymentRepository.findByPaymentKey(PaymentFixture.PAYMENT_KEY)).willReturn(Optional.empty());
			given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

			// when
			ConfirmPreparation preparation = writer.prepare(MEMBER_ID,
					requestOf(PaymentFixture.PAYMENT_KEY, PRICE.longValueExact()));

			// then
			assertThat(preparation.orderId()).isEqualTo(ORDER_ID);
			assertThat(preparation.orderNumber()).isEqualTo(order.getOrderNumber());
			assertThat(preparation.finalAmount()).isEqualByComparingTo(PRICE);
			assertThat(preparation.alreadyApproved()).isEmpty();
		}

		@Test
		@DisplayName("존재하지 않는 주문이면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderNotFound() {
			// given
			given(orderRepository.findByOrderNumberForUpdate(order.getOrderNumber())).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.prepare(MEMBER_ID, requestOf(PaymentFixture.PAYMENT_KEY, 45000)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("타인 소유 주문번호면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderOwnedByOtherMember() {
			// given
			Member other = MemberFixture.withId(MemberFixture.create("other@groove.com"), 999L);
			Order othersOrder = OrderFixture.withId(
					OrderFixture.createWithItem(other, ProductFixture.create(ArtistFixture.withId(1L)), 1), ORDER_ID);
			given(orderRepository.findByOrderNumberForUpdate(othersOrder.getOrderNumber()))
					.willReturn(Optional.of(othersOrder));

			// when & then
			assertThatThrownBy(() -> writer.prepare(MEMBER_ID,
					new PaymentConfirmRequest(PaymentFixture.PAYMENT_KEY, othersOrder.getOrderNumber(), 45000L)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("금액이 일치하지 않으면 ORDER_AMOUNT_MISMATCH 예외를 던진다")
		void throwsWhenAmountMismatch() {
			// given
			given(orderRepository.findByOrderNumberForUpdate(order.getOrderNumber())).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
			given(paymentRepository.findByPaymentKey(PaymentFixture.PAYMENT_KEY)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.prepare(MEMBER_ID, requestOf(PaymentFixture.PAYMENT_KEY, 1L)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_AMOUNT_MISMATCH);
			verify(paymentRepository, never()).save(any());
		}

		@Test
		@DisplayName("이미 결제 완료된 주문이면 PAYMENT_ALREADY_DONE 예외를 던진다")
		void throwsWhenOrderAlreadyPaid() {
			// given
			order.markPaid();
			given(orderRepository.findByOrderNumberForUpdate(order.getOrderNumber())).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
			given(paymentRepository.findByPaymentKey(PaymentFixture.PAYMENT_KEY)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.prepare(MEMBER_ID,
					requestOf(PaymentFixture.PAYMENT_KEY, PRICE.longValueExact())))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_ALREADY_DONE);
		}

		@Test
		@DisplayName("결제 기한이 지난 주문이면 ORDER_EXPIRED 예외를 던진다")
		void throwsWhenOrderExpired() {
			// given
			OrderFixture.withExpiresAt(order, now.minusMinutes(1));
			given(orderRepository.findByOrderNumberForUpdate(order.getOrderNumber())).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
			given(paymentRepository.findByPaymentKey(PaymentFixture.PAYMENT_KEY)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.prepare(MEMBER_ID,
					requestOf(PaymentFixture.PAYMENT_KEY, PRICE.longValueExact())))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_EXPIRED);
		}

		@Test
		@DisplayName("같은 결제 키로 다시 요청하면 기존 승인 응답을 그대로 반환한다")
		void returnsExistingResponseForSamePaymentKeyRetry() {
			// given
			Payment done = PaymentFixture.approved(order);
			given(orderRepository.findByOrderNumberForUpdate(order.getOrderNumber())).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(done));

			// when
			ConfirmPreparation preparation = writer.prepare(MEMBER_ID,
					requestOf(PaymentFixture.PAYMENT_KEY, PRICE.longValueExact()));

			// then
			assertThat(preparation.alreadyApproved()).isPresent();
			assertThat(preparation.alreadyApproved().get().status()).isEqualTo(PaymentStatus.DONE);
			verify(paymentRepository, never()).save(any());
			verify(paymentRepository, never()).findByPaymentKey(any());
		}

		@Test
		@DisplayName("이미 완료된 결제와 다른 키로 요청하면 PAYMENT_ALREADY_DONE 예외를 던진다")
		void throwsWhenDifferentKeyForAlreadyDonePayment() {
			// given
			Payment done = PaymentFixture.approved(order);
			given(orderRepository.findByOrderNumberForUpdate(order.getOrderNumber())).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(done));

			// when & then
			assertThatThrownBy(() -> writer.prepare(MEMBER_ID, requestOf("other-payment-key", 45000)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_ALREADY_DONE);
		}

		@Test
		@DisplayName("다른 주문에 이미 붙은 결제 키면 PAYMENT_KEY_MISMATCH 예외를 던진다")
		void throwsWhenPaymentKeyBelongsToAnotherOrder() {
			// given
			Order anotherOrder = OrderFixture.withId(
					OrderFixture.createWithItem(member, ProductFixture.create(ArtistFixture.withId(2L)), 1), 999L);
			Payment paymentOfAnotherOrder = PaymentFixture.approved(anotherOrder);
			given(orderRepository.findByOrderNumberForUpdate(order.getOrderNumber())).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
			given(paymentRepository.findByPaymentKey(PaymentFixture.PAYMENT_KEY))
					.willReturn(Optional.of(paymentOfAnotherOrder));

			// when & then
			assertThatThrownBy(() -> writer.prepare(MEMBER_ID,
					requestOf(PaymentFixture.PAYMENT_KEY, PRICE.longValueExact())))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_KEY_MISMATCH);
		}

		@Test
		@DisplayName("실패했던 결제가 있으면 새로 만들지 않고 재사용한다")
		void reusesFailedPayment() {
			// given
			Payment failed = PaymentFixture.failed(order, "TOSS REJECT_CARD_COMPANY");
			given(orderRepository.findByOrderNumberForUpdate(order.getOrderNumber())).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(failed));

			// when
			ConfirmPreparation preparation = writer.prepare(MEMBER_ID,
					requestOf(PaymentFixture.PAYMENT_KEY, PRICE.longValueExact()));

			// then
			assertThat(preparation.alreadyApproved()).isEmpty();
			verify(paymentRepository, never()).save(any());
		}
	}

	@Nested
	@DisplayName("approve()")
	class Approve {

		@Test
		@DisplayName("READY 결제를 승인하면 DONE 이 되고 주문도 PAID 로 바뀐다")
		void approvesReadyPaymentAndMarksOrderPaid() {
			// given
			Payment payment = paymentWithId(Payment.ready(order), 10L);
			given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			PaymentConfirmResult result = new PaymentConfirmResult(PaymentFixture.PAYMENT_KEY, order.getOrderNumber(),
					PaymentFixture.METHOD, PRICE, PaymentFixture.APPROVED_AT);

			// when
			PaymentConfirmResponse response = writer.approve(10L, PaymentFixture.PAYMENT_KEY, result);

			// then
			assertThat(response.status()).isEqualTo(PaymentStatus.DONE);
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
			verify(paymentRepository).flush();
		}

		@Test
		@DisplayName("토스 응답에 승인 시각이 없으면 서버 시각을 사용한다")
		void usesServerTimeWhenApprovedAtMissing() {
			// given
			Payment payment = paymentWithId(Payment.ready(order), 11L);
			given(paymentRepository.findById(11L)).willReturn(Optional.of(payment));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			PaymentConfirmResult result = new PaymentConfirmResult(PaymentFixture.PAYMENT_KEY, order.getOrderNumber(),
					PaymentFixture.METHOD, PRICE, null);

			// when
			PaymentConfirmResponse response = writer.approve(11L, PaymentFixture.PAYMENT_KEY, result);

			// then
			assertThat(response.approvedAt()).isEqualTo(now);
		}

		@Test
		@DisplayName("FAILED 결제도 재승인되어 DONE 이 된다")
		void reapprovesFailedPayment() {
			// given
			Payment failed = paymentWithId(PaymentFixture.failed(order, "이전 실패"), 12L);
			given(paymentRepository.findById(12L)).willReturn(Optional.of(failed));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			PaymentConfirmResult result = new PaymentConfirmResult(PaymentFixture.PAYMENT_KEY, order.getOrderNumber(),
					PaymentFixture.METHOD, PRICE, PaymentFixture.APPROVED_AT);

			// when
			PaymentConfirmResponse response = writer.approve(12L, PaymentFixture.PAYMENT_KEY, result);

			// then
			assertThat(response.status()).isEqualTo(PaymentStatus.DONE);
		}

		@Test
		@DisplayName("결제 키가 이미 다른 결제에 쓰여 저장이 충돌하면 PAYMENT_KEY_MISMATCH 예외를 던진다")
		void throwsWhenFlushConflicts() {
			// given
			Payment payment = paymentWithId(Payment.ready(order), 13L);
			given(paymentRepository.findById(13L)).willReturn(Optional.of(payment));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			willThrow(new DataIntegrityViolationException("uk_payment_key")).given(paymentRepository).flush();
			PaymentConfirmResult result = new PaymentConfirmResult(PaymentFixture.PAYMENT_KEY, order.getOrderNumber(),
					PaymentFixture.METHOD, PRICE, PaymentFixture.APPROVED_AT);

			// when & then
			assertThatThrownBy(() -> writer.approve(13L, PaymentFixture.PAYMENT_KEY, result))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_KEY_MISMATCH);
		}
	}

	@Nested
	@DisplayName("fail()")
	class Fail {

		@Test
		@DisplayName("READY 결제는 실패 사유와 함께 FAILED 로 바뀐다")
		void marksReadyPaymentAsFailed() {
			// given
			Payment payment = paymentWithId(Payment.ready(order), 20L);
			given(paymentRepository.findById(20L)).willReturn(Optional.of(payment));

			// when
			writer.fail(20L, "TOSS REJECT_CARD_COMPANY");

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
			assertThat(payment.getFailReason()).isEqualTo("TOSS REJECT_CARD_COMPANY");
		}

		@Test
		@DisplayName("이미 DONE 인 결제는 그대로 둔다")
		void ignoresAlreadyDonePayment() {
			// given
			Payment done = paymentWithId(PaymentFixture.approved(order), 21L);
			given(paymentRepository.findById(21L)).willReturn(Optional.of(done));

			// when
			writer.fail(21L, "동시 요청에서 이미 승인됨");

			// then
			assertThat(done.getStatus()).isEqualTo(PaymentStatus.DONE);
		}
	}
}
