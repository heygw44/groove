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

	@Nested
	@DisplayName("RefundAccount.toString()")
	class ToStringMethod {

		@Test
		@DisplayName("계좌번호 원문 없이 마스킹된 값을 출력한다")
		void masksAccountNumber() {
			// given
			OrderCancelRequest.RefundAccount account = new OrderCancelRequest.RefundAccount("088",
					"110123456789", "홍길동");

			// when
			String text = account.toString();

			// then
			assertThat(text).doesNotContain("110123456789")
					.contains("********6789")
					.contains("088")
					.contains("홍길동");
		}

		@Test
		@DisplayName("OrderCancelRequest 로 감싸 출력해도 계좌번호 원문이 없다")
		void hidesRawAccountNumberInsideRequest() {
			// given
			OrderCancelRequest request = new OrderCancelRequest("단순 변심",
					new OrderCancelRequest.RefundAccount("088", "110123456789", "홍길동"));

			// when
			String text = request.toString();

			// then
			assertThat(text).doesNotContain("110123456789").contains("********6789");
		}
	}
}
