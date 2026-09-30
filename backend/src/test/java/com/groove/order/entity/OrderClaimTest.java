package com.groove.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.payment.client.dto.RefundAccountInfo;

class OrderClaimTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 12, 0);
	private static final RefundAccountInfo ACCOUNT = new RefundAccountInfo("088", "110123456789", "홍길동");

	private OrderItem item;

	@BeforeEach
	void setUp() {
		Order order = OrderFixture.createWithItem(MemberFixture.create(),
				ProductFixture.withId(ProductFixture.create(ArtistFixture.withId(1L)), 200L), 1);
		item = order.getItems().get(0);
	}

	@Nested
	@DisplayName("requestReturn()")
	class RequestReturn {

		@Test
		@DisplayName("환불계좌를 주면 반품 클레임에 계좌를 저장한다")
		void storesRefundAccount() {
			// when
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", ACCOUNT, NOW);

			// then
			assertThat(claim.getType()).isEqualTo(OrderClaimType.RETURN);
			assertThat(claim.getRefundAccount()).isEqualTo(ACCOUNT);
		}

		@Test
		@DisplayName("환불계좌가 null 이면 계좌 없이 만든다")
		void keepsAccountNullWhenNotGiven() {
			// when
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", null, NOW);

			// then
			assertThat(claim.getRefundAccount()).isNull();
		}

		@Test
		@DisplayName("수거를 완료하면 저장한 환불계좌를 지운다")
		void clearsAccountOnComplete() {
			// given
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", ACCOUNT, NOW);
			claim.startCollecting();

			// when
			claim.complete(NOW.plusDays(1), true);

			// then
			assertThat(claim.getRefundAccount()).isNull();
		}

		@Test
		@DisplayName("거부하면 저장한 환불계좌를 지운다")
		void clearsAccountOnReject() {
			// given
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", ACCOUNT, NOW);

			// when
			claim.reject("불가", NOW.plusDays(1));

			// then
			assertThat(claim.getRefundAccount()).isNull();
		}

		@Test
		@DisplayName("철회하면 저장한 환불계좌를 지운다")
		void clearsAccountOnWithdraw() {
			// given
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", ACCOUNT, NOW);

			// when
			claim.withdraw(NOW.plusHours(1));

			// then
			assertThat(claim.getRefundAccount()).isNull();
		}
	}
}
