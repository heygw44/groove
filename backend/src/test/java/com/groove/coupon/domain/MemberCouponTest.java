package com.groove.coupon.domain;

import com.groove.coupon.exception.IllegalCouponStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MemberCoupon — 발급 · 가드 전이 (use/restore/expire/cancel)")
class MemberCouponTest {

    private static final long MEMBER_ID = 42L;
    private static final long ORDER_ID = 7L;

    private static Coupon coupon() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        return Coupon.builder("쿠폰", CouponDiscountType.FIXED_AMOUNT, 1_000, from, from.plus(30, ChronoUnit.DAYS))
                .totalQuantity(100)
                .build();
    }

    private static MemberCoupon issued() {
        return MemberCoupon.issue(coupon(), MEMBER_ID);
    }

    @Test
    @DisplayName("issue: 상태 ISSUED, expiresAt = coupon.validUntil 스냅샷, 미사용")
    void issue() {
        Coupon coupon = coupon();

        MemberCoupon mc = MemberCoupon.issue(coupon, MEMBER_ID);

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(mc.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(mc.getExpiresAt()).isEqualTo(coupon.getValidUntil());
        assertThat(mc.getUsedAt()).isNull();
        assertThat(mc.getOrderId()).isNull();
    }

    @Test
    @DisplayName("use: ISSUED → USED, usedAt·orderId 기록")
    void use() {
        MemberCoupon mc = issued();

        mc.use(ORDER_ID);

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.USED);
        assertThat(mc.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(mc.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("restore: USED → ISSUED, usedAt·orderId 초기화")
    void restore() {
        MemberCoupon mc = issued();
        mc.use(ORDER_ID);

        mc.restore();

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(mc.getUsedAt()).isNull();
        assertThat(mc.getOrderId()).isNull();
    }

    @Test
    @DisplayName("expire: ISSUED → EXPIRED")
    void expire() {
        MemberCoupon mc = issued();

        mc.expire();

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.EXPIRED);
    }

    @Test
    @DisplayName("cancel: ISSUED → CANCELLED")
    void cancel() {
        MemberCoupon mc = issued();

        mc.cancel();

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 사용한 쿠폰 재사용 시도 → IllegalCouponStateTransitionException")
    void use_whenUsed_throws() {
        MemberCoupon mc = issued();
        mc.use(ORDER_ID);

        assertThatThrownBy(() -> mc.use(ORDER_ID))
                .isInstanceOf(IllegalCouponStateTransitionException.class);
    }

    @Test
    @DisplayName("만료(종착) 후 사용 시도 → IllegalCouponStateTransitionException")
    void use_whenExpired_throws() {
        MemberCoupon mc = issued();
        mc.expire();

        assertThatThrownBy(() -> mc.use(ORDER_ID))
                .isInstanceOf(IllegalCouponStateTransitionException.class);
    }

    @Test
    @DisplayName("ISSUED 상태에서 restore(USED→ISSUED 전용) 시도 → 위반")
    void restore_whenIssued_throws() {
        MemberCoupon mc = issued();

        assertThatThrownBy(mc::restore)
                .isInstanceOf(IllegalCouponStateTransitionException.class);
    }
}
