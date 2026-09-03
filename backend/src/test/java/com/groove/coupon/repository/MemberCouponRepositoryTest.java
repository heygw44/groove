package com.groove.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.support.DataJpaTestSupport;

class MemberCouponRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private MemberCouponRepository memberCouponRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private CouponRepository couponRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 회원이 같은 쿠폰을 두 번 발급받으면 유니크 제약 위반이 발생한다")
		void throwsWhenMemberAndCouponDuplicated() {
			// given
			Member member = memberRepository.save(MemberFixture.create("member-coupon-repo-uk@groove.com"));
			Coupon coupon = couponRepository.save(
					CouponFixture.fixed("member-coupon-repo-uk", BigDecimal.valueOf(1000)));
			memberCouponRepository.saveAndFlush(MemberCouponFixture.create(member, coupon));

			// when & then
			assertThatThrownBy(() -> memberCouponRepository.saveAndFlush(
					MemberCouponFixture.create(member, coupon)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}

		@Test
		@DisplayName("다른 회원이 같은 쿠폰을 발급받는 것은 허용된다")
		void allowsDifferentMemberForSameCoupon() {
			// given
			Member first = memberRepository.save(MemberFixture.create("member-coupon-repo-first@groove.com"));
			Member second = memberRepository.save(MemberFixture.create("member-coupon-repo-second@groove.com"));
			Coupon coupon = couponRepository.save(
					CouponFixture.fixed("member-coupon-repo-shared", BigDecimal.valueOf(1000)));
			memberCouponRepository.saveAndFlush(MemberCouponFixture.create(first, coupon));

			// when & then
			assertThatCode(() -> memberCouponRepository.saveAndFlush(MemberCouponFixture.create(second, coupon)))
					.doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("existsByMemberIdAndCouponId()")
	class ExistsByMemberIdAndCouponId {

		@Test
		@DisplayName("발급받은 조합이면 true 를 반환한다")
		void returnsTrueWhenPresent() {
			// given
			Member member = memberRepository.save(MemberFixture.create("member-coupon-repo-exists@groove.com"));
			Coupon coupon = couponRepository.save(
					CouponFixture.fixed("member-coupon-repo-exists", BigDecimal.valueOf(1000)));
			memberCouponRepository.save(MemberCouponFixture.create(member, coupon));

			// when
			boolean exists = memberCouponRepository.existsByMemberIdAndCouponId(member.getId(), coupon.getId());

			// then
			assertThat(exists).isTrue();
		}

		@Test
		@DisplayName("발급받지 않은 조합이면 false 를 반환한다")
		void returnsFalseWhenAbsent() {
			// given
			Member member = memberRepository.save(MemberFixture.create("member-coupon-repo-notexists@groove.com"));
			Coupon coupon = couponRepository.save(
					CouponFixture.fixed("member-coupon-repo-notexists", BigDecimal.valueOf(1000)));

			// when
			boolean exists = memberCouponRepository.existsByMemberIdAndCouponId(member.getId(), coupon.getId());

			// then
			assertThat(exists).isFalse();
		}
	}

	@Nested
	@DisplayName("findUsableByMemberIdAndOrderAmount()")
	class FindUsableByMemberIdAndOrderAmount {

		@Test
		@DisplayName("사용됨/만료/최소금액 미달 쿠폰을 제외하고 사용 가능한 쿠폰만 반환한다")
		void excludesUsedExpiredAndMinOrderAmountNotMet() {
			// given
			Member member = memberRepository.save(MemberFixture.create("member-coupon-repo-usable@groove.com"));
			Coupon usableCoupon = couponRepository.save(
					CouponFixture.fixed("member-coupon-repo-usable", BigDecimal.valueOf(1000)));
			Coupon usedCoupon = couponRepository.save(
					CouponFixture.fixed("member-coupon-repo-used", BigDecimal.valueOf(1000)));
			Coupon expiredCoupon = couponRepository.save(
					CouponFixture.expired(CouponFixture.fixed("member-coupon-repo-expired",
							BigDecimal.valueOf(1000))));
			Coupon minAmountCoupon = couponRepository.save(
					CouponFixture.withMinOrderAmount("member-coupon-repo-minamt", DiscountType.FIXED,
							BigDecimal.valueOf(1000), BigDecimal.valueOf(50000)));

			MemberCoupon usable = memberCouponRepository.save(MemberCouponFixture.create(member, usableCoupon));
			MemberCoupon used = MemberCouponFixture.create(member, usedCoupon);
			used.use(1L);
			memberCouponRepository.save(used);
			memberCouponRepository.save(MemberCouponFixture.create(member, expiredCoupon));
			memberCouponRepository.save(MemberCouponFixture.create(member, minAmountCoupon));

			// when
			List<MemberCoupon> result = memberCouponRepository.findUsableByMemberIdAndOrderAmount(member.getId(),
					BigDecimal.valueOf(10000), LocalDateTime.now());

			// then
			assertThat(result).extracting(MemberCoupon::getId).containsExactly(usable.getId());
		}
	}
}
