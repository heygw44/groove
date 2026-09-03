package com.groove.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.coupon.entity.Coupon;
import com.groove.fixture.CouponFixture;
import com.groove.support.DataJpaTestSupport;

class CouponRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private CouponRepository couponRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("코드가 중복되면 유니크 제약 위반이 발생한다")
		void throwsWhenCodeDuplicated() {
			// given
			couponRepository.saveAndFlush(CouponFixture.fixed("coupon-repo-uk", BigDecimal.valueOf(1000)));

			// when & then
			assertThatThrownBy(() -> couponRepository.saveAndFlush(
					CouponFixture.fixed("coupon-repo-uk", BigDecimal.valueOf(2000))))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Nested
	@DisplayName("findByCode() / existsByCode()")
	class FindByCodeAndExistsByCode {

		@Test
		@DisplayName("등록된 코드면 값을 반환하고 true 를 반환한다")
		void returnsValueAndTrueWhenPresent() {
			// given
			Coupon saved = couponRepository.save(
					CouponFixture.fixed("coupon-repo-present", BigDecimal.valueOf(1000)));

			// when
			Optional<Coupon> found = couponRepository.findByCode("coupon-repo-present");
			boolean exists = couponRepository.existsByCode("coupon-repo-present");

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getId()).isEqualTo(saved.getId());
			assertThat(exists).isTrue();
		}

		@Test
		@DisplayName("등록되지 않은 코드면 empty 와 false 를 반환한다")
		void returnsEmptyAndFalseWhenAbsent() {
			// when
			Optional<Coupon> found = couponRepository.findByCode("coupon-repo-absent");
			boolean exists = couponRepository.existsByCode("coupon-repo-absent");

			// then
			assertThat(found).isEmpty();
			assertThat(exists).isFalse();
		}
	}
}
