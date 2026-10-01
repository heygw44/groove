package com.groove.payment.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.groove.payment.service.PaymentRefundRequest;

class RefundAccountInfoTest {

	private static final String RAW_ACCOUNT_NUMBER = "110123456789";

	@Nested
	@DisplayName("maskAccountNumber()")
	class MaskAccountNumber {

		@Test
		@DisplayName("일반 계좌번호면 뒤 4자리만 남기고 앞을 *로 가린다")
		void keepsLastFourDigits() {
			// when
			String masked = RefundAccountInfo.maskAccountNumber(RAW_ACCOUNT_NUMBER);

			// then
			assertThat(masked).isEqualTo("********6789");
		}

		@ParameterizedTest
		@CsvSource({"1234, ****", "123, ***", "1, *"})
		@DisplayName("4자 이하면 전부 *로 가린다")
		void masksAllWhenFourOrShorter(String accountNumber, String expected) {
			// when & then
			assertThat(RefundAccountInfo.maskAccountNumber(accountNumber)).isEqualTo(expected);
		}

		@Test
		@DisplayName("빈 문자열이면 빈 문자열을 반환한다")
		void returnsEmptyWhenEmpty() {
			// when & then
			assertThat(RefundAccountInfo.maskAccountNumber("")).isEmpty();
		}

		@Test
		@DisplayName("null 이면 null 을 반환한다")
		void returnsNullWhenNull() {
			// when & then
			assertThat(RefundAccountInfo.maskAccountNumber(null)).isNull();
		}

		@Test
		@DisplayName("5자면 앞 1자리만 가린다")
		void masksOnlyFirstWhenFiveDigits() {
			// when & then
			assertThat(RefundAccountInfo.maskAccountNumber("12345")).isEqualTo("*2345");
		}
	}

	@Nested
	@DisplayName("toString()")
	class ToStringMethod {

		@Test
		@DisplayName("계좌번호 원문 없이 마스킹된 값과 나머지 필드를 출력한다")
		void masksAccountNumber() {
			// given
			RefundAccountInfo info = new RefundAccountInfo("088", RAW_ACCOUNT_NUMBER, "홍길동");

			// when
			String text = info.toString();

			// then
			assertThat(text).doesNotContain(RAW_ACCOUNT_NUMBER)
					.contains("********6789")
					.contains("088")
					.contains("홍길동");
		}

		@Test
		@DisplayName("계좌번호가 null 이어도 예외 없이 출력한다")
		void handlesNullAccountNumber() {
			// when & then
			assertThat(new RefundAccountInfo("088", null, "홍길동").toString()).contains("accountNumber=null");
		}

		@Test
		@DisplayName("PaymentCancelCommand 의 toString 에도 계좌번호 원문이 없다")
		void paymentCancelCommandHidesRawAccountNumber() {
			// given
			RefundAccountInfo info = new RefundAccountInfo("088", RAW_ACCOUNT_NUMBER, "홍길동");

			// when
			String text = PaymentCancelCommand.of("pay_key", "단순 변심", BigDecimal.valueOf(1000L), "idem-1", info)
					.toString();

			// then
			assertThat(text).doesNotContain(RAW_ACCOUNT_NUMBER).contains("********6789");
		}

		@Test
		@DisplayName("PaymentRefundRequest 의 toString 에도 계좌번호 원문이 없다")
		void paymentRefundRequestHidesRawAccountNumber() {
			// given
			RefundAccountInfo info = new RefundAccountInfo("088", RAW_ACCOUNT_NUMBER, "홍길동");

			// when
			String text = new PaymentRefundRequest(1L, 2L, "pay_key", BigDecimal.valueOf(1000L), "단순 변심",
					"idem-1", info, 3L).toString();

			// then
			assertThat(text).doesNotContain(RAW_ACCOUNT_NUMBER).contains("********6789");
		}
	}
}
