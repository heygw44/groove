package com.groove.coupon.application;

import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.domain.MemberCouponStatus;
import com.groove.coupon.exception.CouponAlreadyUsedException;
import com.groove.coupon.exception.CouponExpiredException;
import com.groove.coupon.exception.CouponMinOrderNotMetException;
import com.groove.coupon.exception.CouponNotFoundException;
import com.groove.coupon.exception.CouponNotIssuableException;
import com.groove.coupon.exception.CouponNotOwnedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * CouponApplicationService 단위 테스트 — 적용/복원의 검증 분기. 적용은 주문 총액(primitive)만 받고 할인액을
 * 반환한다(Order 엔티티 미참조, #349). 적용 트랜잭션 정합성(재고/쿠폰 USED 동반 롤백)은 통합테스트가 담당한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponApplicationService — 적용/복원 단위 (mocked repository)")
class CouponApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-28T00:00:00Z");
    private static final Duration GRACE = Duration.ofDays(7);
    private static final long MEMBER_ID = 42L;
    private static final long OTHER_MEMBER_ID = 99L;
    private static final long MEMBER_COUPON_ID = 7L;
    private static final long ORDER_ID = 1234L;

    @Mock
    private MemberCouponRepository memberCouponRepository;

    private CouponApplicationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new CouponApplicationService(memberCouponRepository, clock,
                new CouponRestoreProperties(GRACE));
    }

    // --- helpers -------------------------------------------------------------

    private Coupon fixedCoupon(long discount, long minOrder) {
        return Coupon.builder("정액 " + discount, CouponDiscountType.FIXED_AMOUNT, discount,
                        NOW.minus(1, ChronoUnit.DAYS), NOW.plus(10, ChronoUnit.DAYS))
                .minOrderAmount(minOrder)
                .build();
    }

    private MemberCoupon issued(Coupon coupon, long memberId) {
        MemberCoupon mc = MemberCoupon.issue(coupon, memberId, NOW);
        ReflectionTestUtils.setField(mc, "id", MEMBER_COUPON_ID);
        return mc;
    }

    // --- applyToOrder --------------------------------------------------------

    @Test
    @DisplayName("정상 적용 — discount 계산, memberCoupon.use(orderId)")
    void apply_happyPath() {
        Coupon coupon = fixedCoupon(3_000L, 10_000L);
        MemberCoupon mc = issued(coupon, MEMBER_ID);
        when(memberCouponRepository.findByIdForUpdate(MEMBER_COUPON_ID)).thenReturn(Optional.of(mc));

        long discount = service.applyToOrder(MEMBER_COUPON_ID, MEMBER_ID, ORDER_ID, 30_000L);

        assertThat(discount).isEqualTo(3_000L);
        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.USED);
        assertThat(mc.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(mc.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("미존재 memberCouponId → CouponNotFoundException")
    void apply_notFound() {
        when(memberCouponRepository.findByIdForUpdate(MEMBER_COUPON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyToOrder(MEMBER_COUPON_ID, MEMBER_ID, ORDER_ID, 30_000L))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    @DisplayName("타인의 쿠폰 → CouponNotOwnedException, 메시지에 memberId 노출 안 함")
    void apply_notOwned() {
        Coupon coupon = fixedCoupon(3_000L, 0L);
        MemberCoupon mc = issued(coupon, OTHER_MEMBER_ID);
        when(memberCouponRepository.findByIdForUpdate(MEMBER_COUPON_ID)).thenReturn(Optional.of(mc));

        assertThatThrownBy(() -> service.applyToOrder(MEMBER_COUPON_ID, MEMBER_ID, ORDER_ID, 30_000L))
                .isInstanceOf(CouponNotOwnedException.class)
                .hasMessageNotContaining(String.valueOf(OTHER_MEMBER_ID));
        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("USED 상태 쿠폰 → CouponAlreadyUsedException")
    void apply_alreadyUsed() {
        Coupon coupon = fixedCoupon(3_000L, 0L);
        MemberCoupon mc = issued(coupon, MEMBER_ID);
        mc.use(999L, NOW);   // 다른 주문에 이미 사용된 상태
        when(memberCouponRepository.findByIdForUpdate(MEMBER_COUPON_ID)).thenReturn(Optional.of(mc));

        assertThatThrownBy(() -> service.applyToOrder(MEMBER_COUPON_ID, MEMBER_ID, ORDER_ID, 30_000L))
                .isInstanceOf(CouponAlreadyUsedException.class);
    }

    @Test
    @DisplayName("EXPIRED 상태 쿠폰 → CouponExpiredException")
    void apply_expired_status() {
        Coupon coupon = fixedCoupon(3_000L, 0L);
        MemberCoupon mc = issued(coupon, MEMBER_ID);
        mc.expire();
        when(memberCouponRepository.findByIdForUpdate(MEMBER_COUPON_ID)).thenReturn(Optional.of(mc));

        assertThatThrownBy(() -> service.applyToOrder(MEMBER_COUPON_ID, MEMBER_ID, ORDER_ID, 30_000L))
                .isInstanceOf(CouponExpiredException.class);
    }

    @Test
    @DisplayName("CANCELLED 상태 쿠폰 → CouponNotIssuableException (발급 자체 취소 시맨틱)")
    void apply_cancelled_status() {
        Coupon coupon = fixedCoupon(3_000L, 0L);
        MemberCoupon mc = issued(coupon, MEMBER_ID);
        mc.cancel();
        when(memberCouponRepository.findByIdForUpdate(MEMBER_COUPON_ID)).thenReturn(Optional.of(mc));

        assertThatThrownBy(() -> service.applyToOrder(MEMBER_COUPON_ID, MEMBER_ID, ORDER_ID, 30_000L))
                .isInstanceOf(CouponNotIssuableException.class);
    }

    @Test
    @DisplayName("expiresAt 이 now 이전 → CouponExpiredException (스케줄러 미가동 상태에서도 적용 거부)")
    void apply_expired_byTime() {
        Coupon coupon = fixedCoupon(3_000L, 0L);
        MemberCoupon mc = issued(coupon, MEMBER_ID);
        ReflectionTestUtils.setField(mc, "expiresAt", NOW.minus(1, ChronoUnit.HOURS));
        when(memberCouponRepository.findByIdForUpdate(MEMBER_COUPON_ID)).thenReturn(Optional.of(mc));

        assertThatThrownBy(() -> service.applyToOrder(MEMBER_COUPON_ID, MEMBER_ID, ORDER_ID, 30_000L))
                .isInstanceOf(CouponExpiredException.class);
    }

    @Test
    @DisplayName("최소 주문금액 미충족 → CouponMinOrderNotMetException, 쿠폰 미사용 유지")
    void apply_belowMinOrder() {
        Coupon coupon = fixedCoupon(3_000L, 50_000L);
        MemberCoupon mc = issued(coupon, MEMBER_ID);
        when(memberCouponRepository.findByIdForUpdate(MEMBER_COUPON_ID)).thenReturn(Optional.of(mc));

        assertThatThrownBy(() -> service.applyToOrder(MEMBER_COUPON_ID, MEMBER_ID, ORDER_ID, 30_000L))
                .isInstanceOf(CouponMinOrderNotMetException.class);
        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("orderId == null → IllegalArgumentException (호출 규약 위반)")
    void apply_nullOrderId_rejected() {
        assertThatThrownBy(() -> service.applyToOrder(MEMBER_COUPON_ID, MEMBER_ID, null, 10_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- restoreForOrder -----------------------------------------------------

    @Test
    @DisplayName("복원 정상 — USED 쿠폰을 ISSUED 로 되돌리고 usedAt/orderId 비움")
    void restore_happyPath() {
        Coupon coupon = fixedCoupon(3_000L, 0L);
        MemberCoupon mc = issued(coupon, MEMBER_ID);
        mc.use(ORDER_ID, NOW);
        when(memberCouponRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(mc));

        service.restoreForOrder(ORDER_ID);

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(mc.getOrderId()).isNull();
        assertThat(mc.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("복원 시점에 이미 만료된 USED 쿠폰 → 소멸 없이 ISSUED 로 부활, expiresAt = now + grace 연장 (#319)")
    void restore_expiredAtRestoreTime_revivesWithGrace() {
        Coupon coupon = fixedCoupon(3_000L, 0L);
        MemberCoupon mc = issued(coupon, MEMBER_ID);
        mc.use(ORDER_ID, NOW);
        ReflectionTestUtils.setField(mc, "expiresAt", NOW.minus(1, ChronoUnit.HOURS));
        when(memberCouponRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(mc));

        service.restoreForOrder(ORDER_ID);

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(mc.getExpiresAt()).isEqualTo(NOW.plus(GRACE));   // 복원 clock(NOW) + grace
        assertThat(mc.getOrderId()).isNull();
        assertThat(mc.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("USED 쿠폰을 두 번 복원해도 두 번째는 멱등 no-op (무락 동시 복원 안전) (#319)")
    void restore_twice_idempotent() {
        Coupon coupon = fixedCoupon(3_000L, 0L);
        MemberCoupon mc = issued(coupon, MEMBER_ID);
        mc.use(ORDER_ID, NOW);
        when(memberCouponRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(mc));

        service.restoreForOrder(ORDER_ID);
        service.restoreForOrder(ORDER_ID);   // 두 번째: status 가 USED 가 아니라 no-op (예외 없음)

        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(mc.getOrderId()).isNull();
    }

    @Test
    @DisplayName("쿠폰 미적용 주문 복원 → no-op (예외 없음)")
    void restore_noCoupon_noop() {
        when(memberCouponRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        service.restoreForOrder(ORDER_ID);   // 예외 없으면 통과
    }

    @Test
    @DisplayName("이미 ISSUED 상태 쿠폰 복원 → no-op (방어적 가드)")
    void restore_alreadyIssued_noop() {
        Coupon coupon = fixedCoupon(3_000L, 0L);
        MemberCoupon mc = issued(coupon, MEMBER_ID);
        // status 는 ISSUED 인 채 (use 호출 안 함)
        when(memberCouponRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(mc));

        service.restoreForOrder(ORDER_ID);   // 예외 없이 종료
        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("orderId == null → no-op")
    void restore_nullOrderId_noop() {
        service.restoreForOrder(null);   // 예외 없으면 통과
    }
}
