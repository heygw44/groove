package com.groove.order.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.payment.client.dto.RefundAccountInfo;

class OrderCancelRequestTest {

	@Nested
	@DisplayName("RefundAccount.toInfo()")
	class ToInfo {

		@Test
		@DisplayName("null 이면 null 을 반환한다")
		void returnsNullWhenNull() {
			// when & then
			assertThat(OrderCancelRequest.RefundAccount.toInfo(null)).isNull();
		}

		@Test
		@DisplayName("값이 있으면 같은 내용의 RefundAccountInfo 로 바꾼다")
		void convertsFields() {
			// given
			OrderCancelRequest.RefundAccount account = new OrderCancelRequest.RefundAccount("088",
					"110123456789", "홍길동");

			// when
			RefundAccountInfo info = OrderCancelRequest.RefundAccount.toInfo(account);

			// then
			assertThat(info).isEqualTo(new RefundAccountInfo("088", "110123456789", "홍길동"));
		}
	}
}
