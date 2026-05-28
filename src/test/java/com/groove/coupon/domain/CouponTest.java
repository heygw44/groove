package com.groove.coupon.domain;

import com.groove.coupon.exception.CouponMinOrderNotMetException;
import com.groove.coupon.exception.IllegalCouponStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Coupon — 할인 계산 · 정책 생성 검증 · 상태 전이")
class CouponTest {

    private static final Instant VALID_FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant VALID_UNTIL = VALID_FROM.plus(30, ChronoUnit.DAYS);

    private static Coupon fixed(long discountValue, long minOrder) {
        return Coupon.builder("정액쿠폰", CouponDiscountType.FIXED_AMOUNT, discountValue, VALID_FROM, VALID_UNTIL)
                .minOrderAmount(minOrder)
                .build();
    }

    private static Coupon percentage(long rate, Long cap, long minOrder) {
        return Coupon.builder("정률쿠폰", CouponDiscountType.PERCENTAGE, rate, VALID_FROM, VALID_UNTIL)
                .maxDiscountAmount(cap)
                .minOrderAmount(minOrder)
                .build();
    }

    @Nested
    @DisplayName("calculateDiscount — 할인액 산정 경계")
    class CalculateDiscount {

        static Stream<Arguments> discountCases() {
            return Stream.of(
                    // 정액: 소계보다 작은 정액은 그대로
                    Arguments.of(fixed(3_000, 0), 10_000L, 3_000L),
                    // 정액: 소계와 같은 정액
                    Arguments.of(fixed(10_000, 0), 10_000L, 10_000L),
                    // 정액: 소계보다 큰 정액 → 소계로 캡 (discount ≤ subtotal 불변식)
                    Arguments.of(fixed(15_000, 0), 10_000L, 10_000L),
                    // 정률: 10% (캡 없음)
                    Arguments.of(percentage(10, null, 0), 10_000L, 1_000L),
                    // 정률: 절사 (33% of 10,000 = 3,300; 정수 나눗셈)
                    Arguments.of(percentage(33, null, 0), 9_999L, 3_299L),
                    // 정률: 캡 미만 → 원시값
                    Arguments.of(percentage(20, 5_000L, 0), 10_000L, 2_000L),
                    // 정률: 캡 도달 → 캡으로 제한
                    Arguments.of(percentage(20, 1_500L, 0), 10_000L, 1_500L),
                    // 정률: 100% → 소계로 캡 (discount ≤ subtotal)
                    Arguments.of(percentage(100, null, 0), 10_000L, 10_000L),
                    // 최소주문금액 경계: subtotal == minOrder 는 통과
                    Arguments.of(fixed(2_000, 10_000), 10_000L, 2_000L)
            );
        }

        @ParameterizedTest
        @MethodSource("discountCases")
        @DisplayName("정액/정률/캡/최소금액 경계에서 올바른 할인액 (discount ≤ subtotal)")
        void calculateDiscount(Coupon coupon, long subtotal, long expectedDiscount) {
            long discount = coupon.calculateDiscount(subtotal);

            assertThat(discount).isEqualTo(expectedDiscount);
            assertThat(discount).isLessThanOrEqualTo(subtotal);
        }

        @Test
        @DisplayName("subtotal < minOrderAmount → CouponMinOrderNotMetException (422)")
        void belowMinOrder_throws() {
            Coupon coupon = fixed(2_000, 10_000);

            assertThatThrownBy(() -> coupon.calculateDiscount(9_999L))
                    .isInstanceOf(CouponMinOrderNotMetException.class);
        }
    }

    @Nested
    @DisplayName("create — 정책 값 검증 (DB CHECK 이중 방어)")
    class Create {

        @Test
        @DisplayName("유효한 값으로 생성 시 상태 ACTIVE, issuedCount 0")
        void create_valid() {
            Coupon coupon = percentage(10, 5_000L, 30_000);

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
            assertThat(coupon.getIssuedCount()).isZero();
            assertThat(coupon.getDiscountType()).isEqualTo(CouponDiscountType.PERCENTAGE);
        }

        static Stream<Arguments> invalidCases() {
            return Stream.of(
                    Arguments.of("빈 이름", (Runnable) () -> Coupon.builder(" ",
                            CouponDiscountType.FIXED_AMOUNT, 1_000, VALID_FROM, VALID_UNTIL).build()),
                    Arguments.of("discountValue 0", (Runnable) () -> Coupon.builder("쿠폰",
                            CouponDiscountType.FIXED_AMOUNT, 0, VALID_FROM, VALID_UNTIL).build()),
                    Arguments.of("정률 0% (1 미만)", (Runnable) () -> Coupon.builder("쿠폰",
                            CouponDiscountType.PERCENTAGE, 0, VALID_FROM, VALID_UNTIL).build()),
                    Arguments.of("정률 101% (100 초과)", (Runnable) () -> Coupon.builder("쿠폰",
                            CouponDiscountType.PERCENTAGE, 101, VALID_FROM, VALID_UNTIL).build()),
                    Arguments.of("maxDiscountAmount 0", (Runnable) () -> Coupon.builder("쿠폰",
                            CouponDiscountType.PERCENTAGE, 10, VALID_FROM, VALID_UNTIL).maxDiscountAmount(0L).build()),
                    Arguments.of("minOrderAmount 음수", (Runnable) () -> Coupon.builder("쿠폰",
                            CouponDiscountType.FIXED_AMOUNT, 1_000, VALID_FROM, VALID_UNTIL).minOrderAmount(-1).build()),
                    Arguments.of("totalQuantity 음수", (Runnable) () -> Coupon.builder("쿠폰",
                            CouponDiscountType.FIXED_AMOUNT, 1_000, VALID_FROM, VALID_UNTIL).totalQuantity(-1).build()),
                    Arguments.of("perMemberLimit 0", (Runnable) () -> Coupon.builder("쿠폰",
                            CouponDiscountType.FIXED_AMOUNT, 1_000, VALID_FROM, VALID_UNTIL).perMemberLimit(0).build()),
                    Arguments.of("validUntil <= validFrom", (Runnable) () -> Coupon.builder("쿠폰",
                            CouponDiscountType.FIXED_AMOUNT, 1_000, VALID_UNTIL, VALID_FROM).build())
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidCases")
        @DisplayName("제약 위반 값 → IllegalArgumentException")
        void create_invalid(String label, Runnable factory) {
            assertThatThrownBy(factory::run).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("isIssuable — 발급 가능 판정 (상태 · 기간)")
    class IsIssuable {

        @Test
        @DisplayName("ACTIVE + 기간 내 → 발급 가능")
        void activeWithinPeriod_issuable() {
            Coupon coupon = fixed(1_000, 0);

            assertThat(coupon.isIssuable(VALID_FROM)).isTrue();
            assertThat(coupon.isIssuable(VALID_UNTIL)).isTrue();
            assertThat(coupon.isIssuable(VALID_FROM.plus(1, ChronoUnit.DAYS))).isTrue();
        }

        @Test
        @DisplayName("기간 밖(시작 전 · 종료 후) → 발급 불가")
        void outsidePeriod_notIssuable() {
            Coupon coupon = fixed(1_000, 0);

            assertThat(coupon.isIssuable(VALID_FROM.minusMillis(1))).isFalse();
            assertThat(coupon.isIssuable(VALID_UNTIL.plusMillis(1))).isFalse();
        }

        @Test
        @DisplayName("SUSPENDED/ENDED 상태 → 기간 내라도 발급 불가")
        void nonActiveStatus_notIssuable() {
            Coupon suspended = fixed(1_000, 0);
            suspended.changeStatus(CouponStatus.SUSPENDED);
            Coupon ended = fixed(1_000, 0);
            ended.changeStatus(CouponStatus.ENDED);

            assertThat(suspended.isIssuable(VALID_FROM)).isFalse();
            assertThat(ended.isIssuable(VALID_FROM)).isFalse();
        }
    }

    @Nested
    @DisplayName("remainingQuantity — 남은 발급 수량")
    class RemainingQuantity {

        @Test
        @DisplayName("한정수량 쿠폰: total − issued (생성 직후 issued=0)")
        void limited_returnsTotalMinusIssued() {
            Coupon coupon = Coupon.builder("한정", CouponDiscountType.FIXED_AMOUNT, 1_000, VALID_FROM, VALID_UNTIL)
                    .totalQuantity(100)
                    .build();

            assertThat(coupon.remainingQuantity()).isEqualTo(100);
        }

        @Test
        @DisplayName("무제한 쿠폰(totalQuantity null) → null")
        void unlimited_returnsNull() {
            Coupon coupon = fixed(1_000, 0);

            assertThat(coupon.remainingQuantity()).isNull();
        }
    }

    @Nested
    @DisplayName("changeStatus — 상태 전이 가드")
    class ChangeStatus {

        @Test
        @DisplayName("ACTIVE → SUSPENDED → ACTIVE → ENDED 합법 전이")
        void legalTransitions() {
            Coupon coupon = fixed(1_000, 0);

            assertThatCode(() -> {
                coupon.changeStatus(CouponStatus.SUSPENDED);
                coupon.changeStatus(CouponStatus.ACTIVE);
                coupon.changeStatus(CouponStatus.ENDED);
            }).doesNotThrowAnyException();
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ENDED);
        }

        @Test
        @DisplayName("종착(ENDED)에서의 전이 시도 → IllegalCouponStateTransitionException")
        void fromTerminal_throws() {
            Coupon coupon = fixed(1_000, 0);
            coupon.changeStatus(CouponStatus.ENDED);

            assertThatThrownBy(() -> coupon.changeStatus(CouponStatus.ACTIVE))
                    .isInstanceOf(IllegalCouponStateTransitionException.class);
        }
    }
}
