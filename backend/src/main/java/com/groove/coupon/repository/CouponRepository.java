package com.groove.coupon.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.coupon.dto.AdminCouponSummaryResponse;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.CouponStatus;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

	Optional<Coupon> findByCode(String code);

	boolean existsByCode(String code);

	@Query(value = """
			SELECT new com.groove.coupon.dto.AdminCouponSummaryResponse(
				c.id, c.code, c.name, c.discountType, c.discountValue, c.minOrderAmount, c.maxDiscountAmount,
				c.totalQuantity, c.issuedCount,
				(SELECT COUNT(mc) FROM MemberCoupon mc WHERE mc.coupon = c AND mc.used = true),
				c.expiresAt, c.status, c.createdAt)
			FROM Coupon c
			WHERE (:status IS NULL OR c.status = :status)
			""",
			countQuery = "SELECT COUNT(c) FROM Coupon c WHERE (:status IS NULL OR c.status = :status)")
	Page<AdminCouponSummaryResponse> findAdminSummaries(@Param("status") CouponStatus status, Pageable pageable);
}
