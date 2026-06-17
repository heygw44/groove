package com.groove.support;

import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.coupon.domain.MemberCoupon;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 테스트용 쿠폰 픽스처. 정책·회원쿠폰 빌더 단축 + expiresAt 강제 설정 헬퍼.
 */
public final class CouponFixtures {

    private CouponFixtures() {
    }

    private static final Instant DEFAULT_VALID_FROM = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant DEFAULT_VALID_UNTIL = Instant.parse("2999-12-31T00:00:00Z");

    /** 정액 1,000원 · 최소주문 0 · 한정 totalQuantity (null 이면 무제한). */
    public static Coupon fixedAmount(Integer totalQuantity) {
        return Coupon.builder("정액 1,000원", CouponDiscountType.FIXED_AMOUNT, 1_000L,
                        DEFAULT_VALID_FROM, DEFAULT_VALID_UNTIL)
                .totalQuantity(totalQuantity)
                .build();
    }

    /** 정률 10% · 상한 5,000원 · 무제한. */
    public static Coupon percentage10() {
        return Coupon.builder("정률 10%", CouponDiscountType.PERCENTAGE, 10L,
                        DEFAULT_VALID_FROM, DEFAULT_VALID_UNTIL)
                .maxDiscountAmount(5_000L)
                .build();
    }

    /** ISSUED 회원쿠폰 — expiresAt 을 강제 설정해 만료 배치 검증에 사용. */
    public static MemberCoupon issuedWithExpiry(Coupon coupon, Long memberId, Instant expiresAt) {
        MemberCoupon mc = MemberCoupon.issue(coupon, memberId, Instant.now());
        ReflectionTestUtils.setField(mc, "expiresAt", expiresAt);
        return mc;
    }

    public static Instant hoursAgo(long hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }

    public static Instant hoursLater(long hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS);
    }
}
