package com.groove.coupon.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;

class MemberCouponTest {

	private final Member member = MemberFixture.create();
	private final Coupon coupon = CouponFixture.fixed("MC1000", BigDecimal.valueOf(1000));

	@Nested
	@DisplayName("issue()")
	class Issue {

		@Test
		@DisplayName("발급하면 미사용 상태이고 발급 시각이 기록된다")
		void createsUnusedWithIssuedAt() {
			// given & when
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, coupon);

			// then
			assertThat(memberCoupon.isUsed()).isFalse();
			assertThat(memberCoupon.getIssuedAt()).isNotNull();
			assertThat(memberCoupon.getUsedAt()).isNull();
		}
	}

	@Nested
	@DisplayName("use()")
	class Use {

		@Test
		@DisplayName("사용하면 사용 상태로 바뀌고 주문 id·사용 시각이 기록된다")
		void marksUsedWithOrderIdAndUsedAt() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, coupon);

			// when
			memberCoupon.use(1L);

			// then
			assertThat(memberCoupon.isUsed()).isTrue();
			assertThat(memberCoupon.getUsedOrderId()).isEqualTo(1L);
			assertThat(memberCoupon.getUsedAt()).isNotNull();
		}

		@Test
		@DisplayName("주문 id 가 없으면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenOrderIdNull() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, coupon);

			// when & then
			assertThatThrownBy(() -> memberCoupon.use(null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("이미 사용했으면 COUPON_ALREADY_USED 예외를 던진다")
		void throwsWhenAlreadyUsed() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, coupon);
			memberCoupon.use(1L);

			// when & then
			assertThatThrownBy(() -> memberCoupon.use(2L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_ALREADY_USED);
		}
	}

	@Nested
	@DisplayName("restore()")
	class Restore {

		@Test
		@DisplayName("사용 취소하면 미사용 상태로 돌아가고 주문 id·사용 시각이 지워진다")
		void clearsUsedFields() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, coupon);
			memberCoupon.use(1L);

			// when
			memberCoupon.restore();

			// then
			assertThat(memberCoupon.isUsed()).isFalse();
			assertThat(memberCoupon.getUsedOrderId()).isNull();
			assertThat(memberCoupon.getUsedAt()).isNull();
		}

		@Test
		@DisplayName("사용하지 않았으면 COUPON_NOT_USED 예외를 던진다")
		void throwsWhenNotUsed() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, coupon);

			// when & then
			assertThatThrownBy(memberCoupon::restore)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_NOT_USED);
		}
	}

	@Nested
	@DisplayName("calculateDiscount()")
	class CalculateDiscount {

		@Test
		@DisplayName("미사용이면 쿠폰의 할인 계산 결과를 그대로 반환한다")
		void delegatesToCoupon() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, coupon);

			// when
			BigDecimal discount = memberCoupon.calculateDiscount(BigDecimal.valueOf(10000));

			// then
			assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(1000));
		}

		@Test
		@DisplayName("이미 사용했으면 COUPON_ALREADY_USED 예외를 던진다")
		void throwsWhenAlreadyUsed() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, coupon);
			memberCoupon.use(1L);

			// when & then
			assertThatThrownBy(() -> memberCoupon.calculateDiscount(BigDecimal.valueOf(10000)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_ALREADY_USED);
		}
	}

	@Nested
	@DisplayName("isUsable()")
	class IsUsable {

		@Test
		@DisplayName("미사용·미만료·ACTIVE 쿠폰이면 true 를 반환한다")
		void returnsTrueWhenUnusedAndNotExpired() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, coupon);

			// when & then
			assertThat(memberCoupon.isUsable()).isTrue();
		}

		@Test
		@DisplayName("사용했으면 false 를 반환한다")
		void returnsFalseWhenUsed() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, coupon);
			memberCoupon.use(1L);

			// when & then
			assertThat(memberCoupon.isUsable()).isFalse();
		}

		@Test
		@DisplayName("만료됐으면 false 를 반환한다")
		void returnsFalseWhenExpired() {
			// given
			Coupon expiredCoupon = CouponFixture.expired(CouponFixture.fixed("MC-EXPIRED", BigDecimal.valueOf(1000)));
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, expiredCoupon);

			// when & then
			assertThat(memberCoupon.isUsable()).isFalse();
			assertThat(memberCoupon.isExpired()).isTrue();
		}

		@Test
		@DisplayName("쿠폰이 비활성화됐으면 false 를 반환한다")
		void returnsFalseWhenCouponDisabled() {
			// given
			Coupon disabledCoupon = CouponFixture.fixed("MC-DISABLED", BigDecimal.valueOf(1000));
			disabledCoupon.disable();
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, disabledCoupon);

			// when & then
			assertThat(memberCoupon.isUsable()).isFalse();
			assertThat(memberCoupon.isExpired()).isTrue();
		}
	}
}
