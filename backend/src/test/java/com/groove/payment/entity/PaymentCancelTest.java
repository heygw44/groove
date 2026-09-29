package com.groove.payment.entity;

import static com.groove.fixture.PaymentFixture.APPROVED_AT;
import static com.groove.fixture.PaymentFixture.CANCELED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.product.entity.Artist;

class PaymentCancelTest {

	private static final BigDecimal PRICE = new BigDecimal("45000");

	private Payment payment() {
		Member member = MemberFixture.create();
		Artist artist = ArtistFixture.create();
		Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist, "Kind of Blue", PRICE), 2);
		return PaymentFixture.approved(order);
	}

	@Nested
	@DisplayName("request()")
	class Request {

		@Test
		@DisplayName("생성하면 REQUESTED 상태이고 취소 요청 정보를 그대로 담는다")
		void createsWithRequestedStatus() {
			// given
			Payment payment = payment();

			// when
			PaymentCancel paymentCancel = PaymentCancel.request(payment, "cancel-key-1", new BigDecimal("10000"),
					"고객 변심", APPROVED_AT);

			// then
			assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.REQUESTED);
			assertThat(paymentCancel.getIdempotencyKey()).isEqualTo("cancel-key-1");
			assertThat(paymentCancel.getCancelAmount()).isEqualByComparingTo("10000");
			assertThat(paymentCancel.getReason()).isEqualTo("고객 변심");
			assertThat(paymentCancel.getRequestedAt()).isEqualTo(APPROVED_AT);
		}

		@Test
		@DisplayName("사유가 컬럼 길이를 넘으면 200자로 잘라 저장한다")
		void truncatesReasonExceedingColumnLength() {
			// given
			String reason = "가".repeat(201);

			// when
			PaymentCancel paymentCancel = PaymentCancel.request(payment(), "cancel-key-1", new BigDecimal("10000"),
					reason, APPROVED_AT);

			// then
			assertThat(paymentCancel.getReason()).hasSize(200);
		}
	}

	@Nested
	@DisplayName("complete()")
	class Complete {

		@Test
		@DisplayName("REQUESTED 면 DONE 으로 바뀌고 거래 키와 완료 시각이 기록된다")
		void changesStatusToDone() {
			// given
			PaymentCancel paymentCancel = PaymentCancel.request(payment(), "cancel-key-1", new BigDecimal("10000"),
					"고객 변심", APPROVED_AT);

			// when
			paymentCancel.complete("txn-1", CANCELED_AT);

			// then
			assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.DONE);
			assertThat(paymentCancel.getTossTransactionKey()).isEqualTo("txn-1");
			assertThat(paymentCancel.getDoneAt()).isEqualTo(CANCELED_AT);
		}

		@Test
		@DisplayName("REQUESTED 가 아니면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatusWhenNotRequested() {
			// given
			PaymentCancel paymentCancel = PaymentCancel.request(payment(), "cancel-key-1", new BigDecimal("10000"),
					"고객 변심", APPROVED_AT);
			paymentCancel.complete("txn-1", CANCELED_AT);

			// when & then
			assertThatThrownBy(() -> paymentCancel.complete("txn-2", CANCELED_AT))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("fail()")
	class Fail {

		@Test
		@DisplayName("REQUESTED 면 FAILED 로 바뀐다")
		void changesStatusToFailed() {
			// given
			PaymentCancel paymentCancel = PaymentCancel.request(payment(), "cancel-key-1", new BigDecimal("10000"),
					"고객 변심", APPROVED_AT);

			// when
			paymentCancel.fail();

			// then
			assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
		}

		@Test
		@DisplayName("REQUESTED 가 아니면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatusWhenNotRequested() {
			// given
			PaymentCancel paymentCancel = PaymentCancel.request(payment(), "cancel-key-1", new BigDecimal("10000"),
					"고객 변심", APPROVED_AT);
			paymentCancel.fail();

			// when & then
			assertThatThrownBy(paymentCancel::fail)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}
}
