package com.groove.coupon.domain;

import com.groove.coupon.exception.IllegalCouponStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MemberCoupon — 발급 · 가드 전이 (use/restore/expire/cancel)")
class MemberCouponTest {

    private static final long MEMBER_ID = 42L;
    private static final long ORDER_ID = 7L;
    private static final Duration GRACE = Duration.ofDays(7);

    // coupon() 의 validUntil(= expiresAt 스냅샷) = 2026-01-31. 그 전/후 시점을 restore 만료 판정에 쓴다.
    private static final Instant BEFORE_EXPIRY = Instant.parse("2026-01-15T00:00:00Z");
    private static final Instant AFTER_EXPIRY = Instant.parse("2026-02-15T00:00:00Z");

    private static Coupon coupon() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        return Coupon.builder("쿠폰", CouponDiscountType.FIXED_AMOUNT, 1_000, from, from.plus(30, ChronoUnit.DAYS))
                .totalQuantity(100)
                .build();
    }

    private static MemberCoupon issued() {
        return MemberCoupon.issue(coupon(), MEMBER_ID, Instant.now());
    }

    @Test
    @DisplayName("issue: 상태 ISSUED, expiresAt = coupon.validUntil 스냅샷, 미사용")
    void issue() {
        Coupon coupon = coupon();

        MemberCoupon mc = MemberCoupon.issue(coupon, MEMBER_ID, Instant.now());

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

        mc.use(ORDER_ID, Instant.now());

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.USED);
        assertThat(mc.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(mc.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("restore: 미만료 시 USED → ISSUED, expiresAt 보존, usedAt·orderId 초기화")
    void restore_notExpired() {
        MemberCoupon mc = issued();
        Instant originalExpiry = mc.getExpiresAt();
        mc.use(ORDER_ID, Instant.now());

        mc.restore(BEFORE_EXPIRY, GRACE);

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(mc.getExpiresAt()).isEqualTo(originalExpiry);   // 미만료는 연장 안 함
        assertThat(mc.getUsedAt()).isNull();
        assertThat(mc.getOrderId()).isNull();
    }

    @Test
    @DisplayName("restore: 이미 만료된 경우에도 소멸하지 않고 USED → ISSUED, expiresAt = now + grace 연장 (#319)")
    void restore_expired() {
        MemberCoupon mc = issued();
        mc.use(ORDER_ID, Instant.now());

        mc.restore(AFTER_EXPIRY, GRACE);

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(mc.getExpiresAt()).isEqualTo(AFTER_EXPIRY.plus(GRACE));
        assertThat(mc.getUsedAt()).isNull();
        assertThat(mc.getOrderId()).isNull();
    }

    @Test
    @DisplayName("restore: 만료 경계(now == expiresAt) — strict 비교라 만료 아님 → USED → ISSUED, expiresAt 보존")
    void restore_atExpiry() {
        MemberCoupon mc = issued();
        Instant originalExpiry = mc.getExpiresAt();
        mc.use(ORDER_ID, Instant.now());

        mc.restore(mc.getExpiresAt(), GRACE);   // expiresAt.isBefore(now) == false → 연장 없이 ISSUED

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(mc.getExpiresAt()).isEqualTo(originalExpiry);
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
        mc.use(ORDER_ID, Instant.now());

        assertThatThrownBy(() -> mc.use(ORDER_ID, Instant.now()))
                .isInstanceOf(IllegalCouponStateTransitionException.class);
    }

    @Test
    @DisplayName("만료(종착) 후 사용 시도 → IllegalCouponStateTransitionException")
    void use_whenExpired_throws() {
        MemberCoupon mc = issued();
        mc.expire();

        assertThatThrownBy(() -> mc.use(ORDER_ID, Instant.now()))
                .isInstanceOf(IllegalCouponStateTransitionException.class);
    }

    @Test
    @DisplayName("restore: 이미 ISSUED 면 멱등 no-op (예외 없음) (#319)")
    void restore_whenIssued_noop() {
        MemberCoupon mc = issued();

        mc.restore(BEFORE_EXPIRY, GRACE);

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("restore: 두 번 연속 호출해도 두 번째는 멱등 no-op (무락 동시 복원 안전) (#319)")
    void restore_twice_idempotent() {
        MemberCoupon mc = issued();
        mc.use(ORDER_ID, Instant.now());

        mc.restore(BEFORE_EXPIRY, GRACE);
        mc.restore(BEFORE_EXPIRY, GRACE);   // 두 번째: status 가 USED 가 아니라 no-op

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(mc.getOrderId()).isNull();
    }
}
