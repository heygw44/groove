package com.groove.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.groove.coupon.dto.AdminCouponSummaryResponse;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.CouponStatus;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.support.DataJpaTestSupport;

class CouponRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private CouponRepository couponRepository;

	@Autowired
	private MemberCouponRepository memberCouponRepository;

	@Autowired
	private MemberRepository memberRepository;

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

	@Nested
	@DisplayName("findAdminSummaries()")
	class FindAdminSummaries {

		@Test
		@DisplayName("status 로 필터링하고 사용 완료 건수를 함께 반환한다")
		void filtersByStatusAndIncludesUsedCount() {
			// given
			Member member = memberRepository.save(MemberFixture.create("coupon-repo-summary@groove.com"));
			Coupon active = couponRepository.save(CouponFixture.fixed("coupon-repo-summary-active",
					BigDecimal.valueOf(1000)));
			active.issueOne();
			couponRepository.saveAndFlush(active);
			Coupon disabled = couponRepository.save(CouponFixture.fixed("coupon-repo-summary-disabled",
					BigDecimal.valueOf(1000)));
			disabled.disable();
			couponRepository.saveAndFlush(disabled);

			MemberCoupon memberCoupon = memberCouponRepository.save(MemberCouponFixture.create(member, active));
			memberCoupon.use(999L);
			memberCouponRepository.saveAndFlush(memberCoupon);

			// when
			Page<AdminCouponSummaryResponse> activePage = couponRepository.findAdminSummaries(CouponStatus.ACTIVE,
					PageRequest.of(0, 50));
			Page<AdminCouponSummaryResponse> disabledPage = couponRepository.findAdminSummaries(
					CouponStatus.DISABLED, PageRequest.of(0, 50));

			// then
			List<AdminCouponSummaryResponse> activeContent = activePage.getContent();
			AdminCouponSummaryResponse activeSummary = activeContent.stream()
					.filter(summary -> summary.id().equals(active.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(activeSummary.usedCount()).isEqualTo(1L);
			assertThat(activeSummary.issuedCount()).isEqualTo(1);
			assertThat(activeContent).extracting(AdminCouponSummaryResponse::id)
					.doesNotContain(disabled.getId());

			assertThat(disabledPage.getContent()).extracting(AdminCouponSummaryResponse::id)
					.contains(disabled.getId())
					.doesNotContain(active.getId());
		}

		@Test
		@DisplayName("status 가 없으면 전체를 반환한다")
		void returnsAllWhenStatusNull() {
			// given
			Coupon coupon = couponRepository.save(CouponFixture.fixed("coupon-repo-summary-all",
					BigDecimal.valueOf(1000)));

			// when
			Page<AdminCouponSummaryResponse> page = couponRepository.findAdminSummaries(null, PageRequest.of(0, 50));

			// then
			assertThat(page.getContent()).extracting(AdminCouponSummaryResponse::id).contains(coupon.getId());
		}
	}
}
