package com.groove.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.coupon.dto.AvailableCouponResponse;
import com.groove.coupon.dto.CouponIssueRequest;
import com.groove.coupon.dto.CouponIssueResponse;
import com.groove.coupon.dto.MemberCouponResponse;
import com.groove.coupon.dto.MemberCouponStatus;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.coupon.repository.CouponRepository;
import com.groove.coupon.repository.MemberCouponRepository;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class MemberCouponServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final String CODE = "GROOVE10";

	@Mock
	CouponRepository couponRepository;

	@Mock
	MemberCouponRepository memberCouponRepository;

	@Mock
	MemberRepository memberRepository;

	MemberCouponService memberCouponService;

	Member member;

	@BeforeEach
	void setUp() {
		memberCouponService = new MemberCouponService(couponRepository, memberCouponRepository, memberRepository);
		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
	}

	@Nested
	@DisplayName("issue()")
	class Issue {

		@Test
		@DisplayName("정상 발급하면 issuedCount 가 증가하고 발급 결과를 반환한다")
		void issuesCoupon() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.fixed(CODE, BigDecimal.valueOf(1000)), 10L);
			given(couponRepository.findByCodeForUpdate(CODE)).willReturn(Optional.of(coupon));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(memberCouponRepository.existsByMemberIdAndCouponId(MEMBER_ID, coupon.getId())).willReturn(false);
			MemberCoupon saved = MemberCouponFixture.withId(MemberCouponFixture.create(member, coupon), 100L);
			given(memberCouponRepository.saveAndFlush(any())).willReturn(saved);

			// when
			CouponIssueResponse response = memberCouponService.issue(MEMBER_ID, new CouponIssueRequest(CODE));

			// then
			assertThat(coupon.getIssuedCount()).isEqualTo(1);
			assertThat(response.memberCouponId()).isEqualTo(100L);
			assertThat(response.couponCode()).isEqualTo(CODE);
			assertThat(response.couponName()).isEqualTo(coupon.getName());
		}

		@Test
		@DisplayName("존재하지 않는 쿠폰 코드면 COUPON_NOT_FOUND 예외를 던진다")
		void throwsWhenCouponNotFound() {
			// given
			given(couponRepository.findByCodeForUpdate(CODE)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> memberCouponService.issue(MEMBER_ID, new CouponIssueRequest(CODE)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_NOT_FOUND);
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenMemberWithdrawn() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.fixed(CODE, BigDecimal.valueOf(1000)), 10L);
			Member withdrawn = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(couponRepository.findByCodeForUpdate(CODE)).willReturn(Optional.of(coupon));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));

			// when & then
			assertThatThrownBy(() -> memberCouponService.issue(MEMBER_ID, new CouponIssueRequest(CODE)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
			verify(memberCouponRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("이미 발급받은 쿠폰이면 COUPON_ALREADY_ISSUED 예외를 던진다")
		void throwsWhenAlreadyIssued() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.fixed(CODE, BigDecimal.valueOf(1000)), 10L);
			given(couponRepository.findByCodeForUpdate(CODE)).willReturn(Optional.of(coupon));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(memberCouponRepository.existsByMemberIdAndCouponId(MEMBER_ID, coupon.getId())).willReturn(true);

			// when & then
			assertThatThrownBy(() -> memberCouponService.issue(MEMBER_ID, new CouponIssueRequest(CODE)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_ALREADY_ISSUED);
			verify(memberCouponRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("유니크 제약 위반이 발생하면 COUPON_ALREADY_ISSUED 예외로 변환한다")
		void translatesDataIntegrityViolationToAlreadyIssued() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.fixed(CODE, BigDecimal.valueOf(1000)), 10L);
			given(couponRepository.findByCodeForUpdate(CODE)).willReturn(Optional.of(coupon));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(memberCouponRepository.existsByMemberIdAndCouponId(MEMBER_ID, coupon.getId())).willReturn(false);
			given(memberCouponRepository.saveAndFlush(any()))
					.willThrow(new DataIntegrityViolationException("duplicate"));

			// when & then
			assertThatThrownBy(() -> memberCouponService.issue(MEMBER_ID, new CouponIssueRequest(CODE)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_ALREADY_ISSUED);
		}

		@Test
		@DisplayName("발급 수량이 소진됐으면 COUPON_SOLD_OUT 예외를 던진다")
		void throwsWhenSoldOut() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.withTotalQuantity(CODE, 1), 10L);
			CouponFixture.withIssuedCount(coupon, 1);
			given(couponRepository.findByCodeForUpdate(CODE)).willReturn(Optional.of(coupon));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(memberCouponRepository.existsByMemberIdAndCouponId(MEMBER_ID, coupon.getId())).willReturn(false);

			// when & then
			assertThatThrownBy(() -> memberCouponService.issue(MEMBER_ID, new CouponIssueRequest(CODE)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_SOLD_OUT);
		}

		@Test
		@DisplayName("만료된 쿠폰이면 COUPON_EXPIRED 예외를 던진다")
		void throwsWhenExpired() {
			// given
			Coupon coupon = CouponFixture.withId(
					CouponFixture.expired(CouponFixture.fixed(CODE, BigDecimal.valueOf(1000))), 10L);
			given(couponRepository.findByCodeForUpdate(CODE)).willReturn(Optional.of(coupon));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(memberCouponRepository.existsByMemberIdAndCouponId(MEMBER_ID, coupon.getId())).willReturn(false);

			// when & then
			assertThatThrownBy(() -> memberCouponService.issue(MEMBER_ID, new CouponIssueRequest(CODE)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_EXPIRED);
		}

		@Test
		@DisplayName("비활성화된 쿠폰이면 COUPON_DISABLED 예외를 던진다")
		void throwsWhenDisabled() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.fixed(CODE, BigDecimal.valueOf(1000)), 10L);
			coupon.disable();
			given(couponRepository.findByCodeForUpdate(CODE)).willReturn(Optional.of(coupon));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(memberCouponRepository.existsByMemberIdAndCouponId(MEMBER_ID, coupon.getId())).willReturn(false);

			// when & then
			assertThatThrownBy(() -> memberCouponService.issue(MEMBER_ID, new CouponIssueRequest(CODE)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_DISABLED);
		}
	}

	@Nested
	@DisplayName("getMyCoupons()")
	class GetMyCoupons {

		@Test
		@DisplayName("상태 필터가 없으면 전체 쿠폰을 반환한다")
		void returnsAllWhenStatusNull() {
			// given
			Coupon usableCoupon = CouponFixture.withId(CouponFixture.fixed("USABLE10", BigDecimal.valueOf(1000)),
					1L);
			Coupon expiredCoupon = CouponFixture.withId(
					CouponFixture.expired(CouponFixture.fixed("EXPIRED10", BigDecimal.valueOf(1000))), 2L);
			MemberCoupon usable = MemberCouponFixture.withId(MemberCouponFixture.create(member, usableCoupon), 1L);
			MemberCoupon used = MemberCouponFixture.withId(
					MemberCouponFixture.used(MemberCouponFixture.create(member, usableCoupon), 1L), 2L);
			MemberCoupon expired = MemberCouponFixture.withId(MemberCouponFixture.create(member, expiredCoupon), 3L);
			given(memberCouponRepository.findAllWithCouponByMemberId(MEMBER_ID))
					.willReturn(List.of(usable, used, expired));

			// when
			List<MemberCouponResponse> responses = memberCouponService.getMyCoupons(MEMBER_ID, null);

			// then
			assertThat(responses).hasSize(3);
		}

		@Test
		@DisplayName("USABLE 이면 미사용·미만료·ACTIVE 쿠폰만 반환한다")
		void filtersUsable() {
			// given
			Coupon usableCoupon = CouponFixture.withId(CouponFixture.fixed("USABLE10", BigDecimal.valueOf(1000)),
					1L);
			Coupon expiredCoupon = CouponFixture.withId(
					CouponFixture.expired(CouponFixture.fixed("EXPIRED10", BigDecimal.valueOf(1000))), 2L);
			MemberCoupon usable = MemberCouponFixture.withId(MemberCouponFixture.create(member, usableCoupon), 1L);
			MemberCoupon used = MemberCouponFixture.withId(
					MemberCouponFixture.used(MemberCouponFixture.create(member, usableCoupon), 1L), 2L);
			MemberCoupon expired = MemberCouponFixture.withId(MemberCouponFixture.create(member, expiredCoupon), 3L);
			given(memberCouponRepository.findAllWithCouponByMemberId(MEMBER_ID))
					.willReturn(List.of(usable, used, expired));

			// when
			List<MemberCouponResponse> responses = memberCouponService.getMyCoupons(MEMBER_ID,
					MemberCouponStatus.USABLE);

			// then
			assertThat(responses).extracting(MemberCouponResponse::memberCouponId).containsExactly(1L);
		}

		@Test
		@DisplayName("USED 면 사용한 쿠폰만 반환한다")
		void filtersUsed() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.fixed("USED10", BigDecimal.valueOf(1000)), 1L);
			MemberCoupon usable = MemberCouponFixture.withId(MemberCouponFixture.create(member, coupon), 1L);
			MemberCoupon used = MemberCouponFixture.withId(
					MemberCouponFixture.used(MemberCouponFixture.create(member, coupon), 1L), 2L);
			given(memberCouponRepository.findAllWithCouponByMemberId(MEMBER_ID))
					.willReturn(List.of(usable, used));

			// when
			List<MemberCouponResponse> responses = memberCouponService.getMyCoupons(MEMBER_ID,
					MemberCouponStatus.USED);

			// then
			assertThat(responses).extracting(MemberCouponResponse::memberCouponId).containsExactly(2L);
		}

		@Test
		@DisplayName("EXPIRED 면 미사용이면서 만료된 쿠폰만 반환한다")
		void filtersExpired() {
			// given
			Coupon usableCoupon = CouponFixture.withId(CouponFixture.fixed("USABLE10", BigDecimal.valueOf(1000)),
					1L);
			Coupon expiredCoupon = CouponFixture.withId(
					CouponFixture.expired(CouponFixture.fixed("EXPIRED10", BigDecimal.valueOf(1000))), 2L);
			MemberCoupon usable = MemberCouponFixture.withId(MemberCouponFixture.create(member, usableCoupon), 1L);
			MemberCoupon expired = MemberCouponFixture.withId(MemberCouponFixture.create(member, expiredCoupon), 2L);
			given(memberCouponRepository.findAllWithCouponByMemberId(MEMBER_ID))
					.willReturn(List.of(usable, expired));

			// when
			List<MemberCouponResponse> responses = memberCouponService.getMyCoupons(MEMBER_ID,
					MemberCouponStatus.EXPIRED);

			// then
			assertThat(responses).extracting(MemberCouponResponse::memberCouponId).containsExactly(2L);
		}
	}

	@Nested
	@DisplayName("getAvailableCoupons()")
	class GetAvailableCoupons {

		@Test
		@DisplayName("예상 할인액 내림차순으로 정렬해 반환한다")
		void sortsByExpectedDiscountDescending() {
			// given
			Coupon smallDiscount = CouponFixture.withId(CouponFixture.fixed("SMALL1000", BigDecimal.valueOf(1000)),
					1L);
			Coupon bigDiscount = CouponFixture.withId(CouponFixture.fixed("BIG5000", BigDecimal.valueOf(5000)), 2L);
			MemberCoupon small = MemberCouponFixture.withId(MemberCouponFixture.create(member, smallDiscount), 1L);
			MemberCoupon big = MemberCouponFixture.withId(MemberCouponFixture.create(member, bigDiscount), 2L);
			given(memberCouponRepository.findUsableByMemberIdAndOrderAmount(eq(MEMBER_ID), any(), any()))
					.willReturn(List.of(small, big));

			// when
			List<AvailableCouponResponse> responses = memberCouponService.getAvailableCoupons(MEMBER_ID,
					BigDecimal.valueOf(10000));

			// then
			assertThat(responses).extracting(AvailableCouponResponse::memberCouponId).containsExactly(2L, 1L);
		}

		@Test
		@DisplayName("주문 금액이 음수면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenOrderAmountNegative() {
			// when & then
			assertThatThrownBy(
					() -> memberCouponService.getAvailableCoupons(MEMBER_ID, BigDecimal.valueOf(-1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}
	}
}
