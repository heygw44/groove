package com.groove.coupon.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.coupon.dto.AvailableCouponResponse;
import com.groove.coupon.dto.CouponIssueRequest;
import com.groove.coupon.dto.CouponIssueResponse;
import com.groove.coupon.dto.MemberCouponResponse;
import com.groove.coupon.dto.MemberCouponStatus;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.coupon.repository.CouponRepository;
import com.groove.coupon.repository.MemberCouponRepository;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** 쿠폰 발급, 내 쿠폰함 조회, 주문 시 적용 가능한 쿠폰 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberCouponService {

	private final CouponRepository couponRepository;
	private final MemberCouponRepository memberCouponRepository;
	private final MemberRepository memberRepository;

	@Transactional
	public CouponIssueResponse issue(Long memberId, CouponIssueRequest request) {
		Coupon coupon = couponRepository.findByCodeForUpdate(request.code())
				.orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
		Member member = findActiveMember(memberId);

		if (memberCouponRepository.existsByMemberIdAndCouponId(memberId, coupon.getId())) {
			throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
		}

		coupon.issueOne();

		MemberCoupon saved;
		try {
			saved = memberCouponRepository.saveAndFlush(MemberCoupon.issue(member, coupon));
		} catch (DataIntegrityViolationException | CannotAcquireLockException ex) {
			// REPEATABLE READ 스냅샷 때문에 선검사만으로는 경합을 못 막아 유니크 제약 위반을 여기서 한 번 더 잡는다.
			// InnoDB 는 같은 유니크 키로 INSERT 가 몰리면 중복키 대신 데드락을 내므로 락 획득 실패도 같이 잡는다.
			throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
		}

		return CouponIssueResponse.from(saved);
	}

	public List<MemberCouponResponse> getMyCoupons(Long memberId, MemberCouponStatus status) {
		return memberCouponRepository.findAllWithCouponByMemberId(memberId).stream()
				.filter(memberCoupon -> matches(memberCoupon, status))
				.map(MemberCouponResponse::from)
				.toList();
	}

	public List<AvailableCouponResponse> getAvailableCoupons(Long memberId, BigDecimal orderAmount) {
		if (orderAmount == null || orderAmount.signum() < 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}

		List<MemberCoupon> usableCoupons = memberCouponRepository.findUsableByMemberIdAndOrderAmount(memberId,
				orderAmount, LocalDateTime.now());

		return usableCoupons.stream()
				.map(memberCoupon -> AvailableCouponResponse.of(memberCoupon,
						memberCoupon.calculateDiscount(orderAmount)))
				.sorted(Comparator.comparing(AvailableCouponResponse::expectedDiscount).reversed()
						.thenComparing(AvailableCouponResponse::expiresAt)
						.thenComparing(AvailableCouponResponse::memberCouponId))
				.toList();
	}

	private boolean matches(MemberCoupon memberCoupon, MemberCouponStatus status) {
		if (status == null) {
			return true;
		}
		return switch (status) {
			case USABLE -> memberCoupon.isUsable();
			case USED -> memberCoupon.isUsed();
			case EXPIRED -> !memberCoupon.isUsed() && memberCoupon.isExpired();
		};
	}

	private Member findActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (member.isWithdrawn()) {
			throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN);
		}
		return member;
	}
}
