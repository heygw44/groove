package com.groove.coupon.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.groove.fixture.CouponFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

class CouponTest {

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("생성하면 ACTIVE 상태이고 발급 수량은 0이다")
		void createsWithActiveStatusAndZeroIssuedCount() {
			// given & when
			Coupon coupon = CouponFixture.fixed("WELCOME1000", BigDecimal.valueOf(1000));

			// then
			assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
			assertThat(coupon.getIssuedCount()).isZero();
		}

		@Test
		@DisplayName("최소 주문 금액을 지정하지 않으면 0으로 저장된다")
		void defaultsMinOrderAmountToZeroWhenNull() {
			// given & when
			Coupon coupon = CouponFixture.create("NOMIN1000", DiscountType.FIXED, BigDecimal.valueOf(1000), null,
					null, null, LocalDateTime.now().plusDays(1));

			// then
			assertThat(coupon.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.ZERO);
		}

		@Test
		@DisplayName("FIXED 타입은 최대 할인 한도를 지정해도 저장하지 않는다")
		void ignoresMaxDiscountAmountForFixedType() {
			// given & when
			Coupon coupon = CouponFixture.create("FIXEDMAX", DiscountType.FIXED, BigDecimal.valueOf(1000),
					BigDecimal.ZERO, BigDecimal.valueOf(5000), null, LocalDateTime.now().plusDays(1));

			// then
			assertThat(coupon.getMaxDiscountAmount()).isNull();
		}

		@ParameterizedTest
		@CsvSource({
			"0",
			"-1000"
		})
		@DisplayName("할인 값이 0 이하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenDiscountValueNotPositive(BigDecimal discountValue) {
			// when & then
			assertThatThrownBy(() -> CouponFixture.create("BADVALUE", DiscountType.FIXED, discountValue,
					BigDecimal.ZERO, null, null, LocalDateTime.now().plusDays(1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("RATE 타입 할인 값이 100을 초과하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenRateDiscountValueExceeds100() {
			// when & then
			assertThatThrownBy(() -> CouponFixture.create("BADRATE", DiscountType.RATE, BigDecimal.valueOf(101),
					BigDecimal.ZERO, null, null, LocalDateTime.now().plusDays(1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("최소 주문 금액이 음수면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenMinOrderAmountNegative() {
			// when & then
			assertThatThrownBy(() -> CouponFixture.create("NEGMIN", DiscountType.FIXED, BigDecimal.valueOf(1000),
					BigDecimal.valueOf(-1), null, null, LocalDateTime.now().plusDays(1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("최대 할인 한도가 0 이하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenMaxDiscountAmountNotPositive() {
			// when & then
			assertThatThrownBy(() -> CouponFixture.create("BADMAX", DiscountType.RATE, BigDecimal.valueOf(10),
					BigDecimal.ZERO, BigDecimal.ZERO, null, LocalDateTime.now().plusDays(1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("총 발급 수량이 0 이하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenTotalQuantityNotPositive() {
			// when & then
			assertThatThrownBy(() -> CouponFixture.create("BADQTY", DiscountType.FIXED, BigDecimal.valueOf(1000),
					BigDecimal.ZERO, null, 0, LocalDateTime.now().plusDays(1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("만료일이 현재 시각보다 이후가 아니면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenExpiresAtNotInFuture() {
			// when & then
			assertThatThrownBy(() -> CouponFixture.create("BADEXP", DiscountType.FIXED, BigDecimal.valueOf(1000),
					BigDecimal.ZERO, null, null, LocalDateTime.now().minusDays(1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}
	}

	@Nested
	@DisplayName("calculateDiscount()")
	class CalculateDiscount {

		@Test
		@DisplayName("FIXED 타입이면 정액 할인 값을 반환한다")
		void returnsFixedDiscountValue() {
			// given
			Coupon coupon = CouponFixture.fixed("FIXED1000", BigDecimal.valueOf(1000));

			// when
			BigDecimal discount = coupon.calculateDiscount(BigDecimal.valueOf(50000));

			// then
			assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(1000));
		}

		@Test
		@DisplayName("RATE 타입이고 한도가 없으면 정률 계산 결과를 그대로 반환한다")
		void returnsRateDiscountWithoutCap() {
			// given
			Coupon coupon = CouponFixture.rate("RATE10", BigDecimal.valueOf(10), null);

			// when
			BigDecimal discount = coupon.calculateDiscount(BigDecimal.valueOf(30000));

			// then
			assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3000));
		}

		@Test
		@DisplayName("RATE 타입이고 한도가 있으면 한도를 초과하지 않는다")
		void capsRateDiscountAtMaxAmount() {
			// given
			Coupon coupon = CouponFixture.rate("RATE20", BigDecimal.valueOf(20), BigDecimal.valueOf(3000));

			// when
			BigDecimal discount = coupon.calculateDiscount(BigDecimal.valueOf(50000));

			// then
			assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3000));
		}

		@Test
		@DisplayName("정률 계산 결과는 소수점 둘째 자리 아래를 버림한다")
		void truncatesRateDiscountToScale2() {
			// given
			Coupon coupon = CouponFixture.rate("RATE33", BigDecimal.valueOf(33), null);

			// when
			BigDecimal discount = coupon.calculateDiscount(BigDecimal.valueOf(100));

			// then
			assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(33));
		}

		@Test
		@DisplayName("FIXED 할인 값이 주문 금액보다 크면 주문 금액으로 제한된다")
		void capsFixedDiscountAtOrderAmount() {
			// given
			Coupon coupon = CouponFixture.fixed("BIGFIXED", BigDecimal.valueOf(10000));

			// when
			BigDecimal discount = coupon.calculateDiscount(BigDecimal.valueOf(3000));

			// then
			assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3000));
		}

		@ParameterizedTest
		@CsvSource({
			"9999, false",
			"10000, true"
		})
		@DisplayName("최소 주문 금액 미만이면 COUPON_MIN_ORDER_AMOUNT_NOT_MET 예외를 던지고 이상이면 통과한다")
		void validatesMinOrderAmountBoundary(BigDecimal orderAmount, boolean shouldPass) {
			// given
			Coupon coupon = CouponFixture.withMinOrderAmount("MINBOUND", DiscountType.FIXED,
					BigDecimal.valueOf(1000), BigDecimal.valueOf(10000));

			// when & then
			if (shouldPass) {
				assertThat(coupon.calculateDiscount(orderAmount)).isEqualByComparingTo(BigDecimal.valueOf(1000));
			} else {
				assertThatThrownBy(() -> coupon.calculateDiscount(orderAmount))
						.isInstanceOf(BusinessException.class)
						.extracting("errorCode")
						.isEqualTo(ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET);
			}
		}

		@Test
		@DisplayName("만료되었으면 COUPON_EXPIRED 예외를 던진다")
		void throwsWhenExpired() {
			// given
			Coupon coupon = CouponFixture.expired(CouponFixture.fixed("EXPIRED", BigDecimal.valueOf(1000)));

			// when & then
			assertThatThrownBy(() -> coupon.calculateDiscount(BigDecimal.valueOf(10000)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_EXPIRED);
		}

		@Test
		@DisplayName("사용 중지 상태면 COUPON_DISABLED 예외를 던진다")
		void throwsWhenDisabled() {
			// given
			Coupon coupon = CouponFixture.fixed("DISABLED1", BigDecimal.valueOf(1000));
			coupon.disable();

			// when & then
			assertThatThrownBy(() -> coupon.calculateDiscount(BigDecimal.valueOf(10000)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_DISABLED);
		}
	}

	@Nested
	@DisplayName("issueOne()")
	class IssueOne {

		@Test
		@DisplayName("발급하면 발급 수량이 1 증가한다")
		void incrementsIssuedCount() {
			// given
			Coupon coupon = CouponFixture.withTotalQuantity("ISSUE1", 10);

			// when
			coupon.issueOne();

			// then
			assertThat(coupon.getIssuedCount()).isEqualTo(1);
		}

		@Test
		@DisplayName("총 발급 수량에 도달하면 COUPON_SOLD_OUT 예외를 던진다")
		void throwsSoldOutWhenReachedTotalQuantity() {
			// given
			Coupon coupon = CouponFixture.withIssuedCount(CouponFixture.withTotalQuantity("SOLDOUT1", 5), 5);

			// when & then
			assertThatThrownBy(coupon::issueOne)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_SOLD_OUT);
		}

		@Test
		@DisplayName("총 발급 수량이 없으면 소진되지 않는다")
		void neverSoldOutWhenTotalQuantityIsNull() {
			// given
			Coupon coupon = CouponFixture.withIssuedCount(CouponFixture.fixed("UNLIMITED1", BigDecimal.valueOf(1000)),
					10_000);

			// when
			coupon.issueOne();

			// then
			assertThat(coupon.getIssuedCount()).isEqualTo(10_001);
		}

		@Test
		@DisplayName("만료되었으면 COUPON_EXPIRED 예외를 던진다")
		void throwsWhenExpired() {
			// given
			Coupon coupon = CouponFixture.expired(CouponFixture.withTotalQuantity("ISSUEEXP", 10));

			// when & then
			assertThatThrownBy(coupon::issueOne)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_EXPIRED);
		}

		@Test
		@DisplayName("사용 중지 상태면 COUPON_DISABLED 예외를 던진다")
		void throwsWhenDisabled() {
			// given
			Coupon coupon = CouponFixture.withTotalQuantity("ISSUEDISABLED", 10);
			coupon.disable();

			// when & then
			assertThatThrownBy(coupon::issueOne)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_DISABLED);
		}
	}

	@Nested
	@DisplayName("disable()")
	class Disable {

		@Test
		@DisplayName("호출하면 상태가 DISABLED 로 바뀐다")
		void changesStatusToDisabled() {
			// given
			Coupon coupon = CouponFixture.fixed("TOGGLE1", BigDecimal.valueOf(1000));

			// when
			coupon.disable();

			// then
			assertThat(coupon.getStatus()).isEqualTo(CouponStatus.DISABLED);
		}

		@Test
		@DisplayName("이미 DISABLED 상태여도 다시 호출해도 안전하다")
		void isIdempotent() {
			// given
			Coupon coupon = CouponFixture.fixed("TOGGLE2", BigDecimal.valueOf(1000));
			coupon.disable();

			// when
			coupon.disable();

			// then
			assertThat(coupon.getStatus()).isEqualTo(CouponStatus.DISABLED);
		}
	}
}
