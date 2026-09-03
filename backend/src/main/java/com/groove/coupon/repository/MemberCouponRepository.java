package com.groove.coupon.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.coupon.entity.MemberCoupon;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

	boolean existsByMemberIdAndCouponId(Long memberId, Long couponId);

	Optional<MemberCoupon> findByIdAndMemberId(Long id, Long memberId);
}
