package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
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
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.service.OrderClaimFinalizeService;
import com.groove.payment.entity.Payment;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderClaimRefundServiceTest {

	private static final Long ORDER_ID = 10L;
	private static final Long PAYMENT_ID = 20L;
	private static final Long CLAIM_ID = 500L;
	private static final BigDecimal AMOUNT = new BigDecimal("10000");

	@Mock
	PaymentRepository paymentRepository;

	@Mock
	PaymentRefundService paymentRefundService;

	@Mock
	OrderClaimFinalizeService orderClaimFinalizeService;

	OrderClaimRefundService service;
	Payment payment;

	@BeforeEach
	void setUp() {
		service = new OrderClaimRefundService(paymentRepository, paymentRefundService, orderClaimFinalizeService);
		Member member = MemberFixture.create();
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 200L);
		Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
		payment = PaymentFixture.approved(order);
		ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));
	}

	@Nested
	@DisplayName("refund()")
	class Refund {

		@Test
		@DisplayName("환불이 DONE 이면 클레임을 마무리한다")
		void finalizesWhenDone() {
			// given
			given(paymentRefundService.refund(PAYMENT_ID, AMOUNT, "사유", null, CLAIM_ID))
					.willReturn(new PaymentRefundResult(PaymentRefundStatus.DONE, 90L, AMOUNT));

			// when
			service.refund(ORDER_ID, CLAIM_ID, AMOUNT, "사유", null);

			// then
			verify(orderClaimFinalizeService).applyRefundDone(CLAIM_ID, null);
		}

		@Test
		@DisplayName("환불이 IN_PROGRESS 면 클레임을 건드리지 않고 대사에 맡긴다")
		void doesNothingWhenInProgress() {
			// given
			given(paymentRefundService.refund(PAYMENT_ID, AMOUNT, "사유", null, CLAIM_ID))
					.willReturn(new PaymentRefundResult(PaymentRefundStatus.IN_PROGRESS, 90L, null));

			// when
			service.refund(ORDER_ID, CLAIM_ID, AMOUNT, "사유", null);

			// then
			verify(orderClaimFinalizeService, never()).applyRefundDone(any(), any());
		}

		@Test
		@DisplayName("결과불명이면 클레임을 건드리지 않고 조용히 반환한다")
		void doesNothingWhenResultUnknown() {
			// given
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN)).given(paymentRefundService)
					.refund(PAYMENT_ID, AMOUNT, "사유", null, CLAIM_ID);

			// when
			service.refund(ORDER_ID, CLAIM_ID, AMOUNT, "사유", null);

			// then
			verify(orderClaimFinalizeService, never()).applyRefundFailed(any());
		}

		@Test
		@DisplayName("토스가 명시적으로 거절하면 클레임을 되돌린 뒤 예외를 다시 던진다")
		void revertsAndRethrowsWhenRejected() {
			// given
			BusinessException failure = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
			willThrow(failure).given(paymentRefundService).refund(PAYMENT_ID, AMOUNT, "사유", null, CLAIM_ID);

			// when & then
			assertThatThrownBy(() -> service.refund(ORDER_ID, CLAIM_ID, AMOUNT, "사유", null)).isSameAs(failure);
			verify(orderClaimFinalizeService).applyRefundFailed(CLAIM_ID);
		}

		@Test
		@DisplayName("결제가 없으면 PAYMENT_NOT_FOUND 예외를 던진다")
		void throwsWhenPaymentNotFound() {
			// given
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> service.refund(ORDER_ID, CLAIM_ID, AMOUNT, "사유", null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
		}
	}
}
