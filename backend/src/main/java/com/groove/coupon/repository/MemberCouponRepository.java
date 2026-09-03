package com.groove.coupon.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.coupon.entity.MemberCoupon;

import jakarta.persistence.LockModeType;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

	boolean existsByMemberIdAndCouponId(Long memberId, Long couponId);

	Optional<MemberCoupon> findByIdAndMemberId(Long id, Long memberId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select mc from MemberCoupon mc join fetch mc.coupon c
			where mc.id = :id and mc.member.id = :memberId
			""")
	Optional<MemberCoupon> findWithCouponByIdAndMemberIdForUpdate(@Param("id") Long id,
			@Param("memberId") Long memberId);

	@Query("""
			select mc from MemberCoupon mc join fetch mc.coupon c
			where mc.member.id = :memberId
			order by mc.issuedAt desc, mc.id desc
			""")
	List<MemberCoupon> findAllWithCouponByMemberId(@Param("memberId") Long memberId);

	@Query("""
			select mc from MemberCoupon mc join fetch mc.coupon c
			where mc.member.id = :memberId and mc.used = false
			and c.status = com.groove.coupon.entity.CouponStatus.ACTIVE
			and c.expiresAt > :now and c.minOrderAmount <= :orderAmount
			""")
	List<MemberCoupon> findUsableByMemberIdAndOrderAmount(@Param("memberId") Long memberId,
			@Param("orderAmount") BigDecimal orderAmount, @Param("now") LocalDateTime now);
}
