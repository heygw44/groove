package com.groove.coupon.application;

import com.groove.coupon.domain.CouponDiscountType;

import java.time.Instant;

/**
 * 쿠폰 정책 생성 입력. coupon 도메인이 소유하는 application 커맨드라 관리자 API DTO(admin.api.dto)를
 * 역참조하지 않게 한다(coupon→admin 순환 차단, #349). 형식 검증은 admin DTO 의 빈 검증이, 의미 검증은
 * Coupon.Builder.build() 가 담당하므로 이 커맨드에는 검증 애노테이션을 두지 않는다.
 */
public record CouponCreateCommand(
        String name,
        CouponDiscountType discountType,
        long discountValue,
        Long maxDiscountAmount,
        long minOrderAmount,
        Integer totalQuantity,
        int perMemberLimit,
        Instant validFrom,
        Instant validUntil
) {
}
