package com.groove.coupon.application;

import com.groove.coupon.api.dto.MemberCouponResponse;
import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.CouponStatus;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.domain.MemberCouponStatus;
import com.groove.coupon.exception.CouponAlreadyIssuedException;
import com.groove.coupon.exception.CouponNotFoundException;
import com.groove.coupon.exception.CouponNotIssuableException;
import com.groove.coupon.exception.CouponSoldOutException;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CouponIssueService 단위 테스트 — 발급 경로(issue)의 검증 분기.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponIssueService — 발급 검증 분기 (mocked repository)")
class CouponIssueServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-27T00:00:00Z");
    private static final long MEMBER_ID = 42L;
    private static final long COUPON_ID = 7L;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private MemberCouponRepository memberCouponRepository;

    @Mock
    private MemberRepository memberRepository;

    private CouponIssueService service;

    private Coupon coupon;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new CouponIssueService(couponRepository, memberCouponRepository, memberRepository, clock);
        // 활성 회원 기본값. 탈퇴 시나리오만 false 로 override 한다.
        lenient().when(memberRepository.existsByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(true);
        coupon = Coupon.builder("정액 3천원", CouponDiscountType.FIXED_AMOUNT, 3_000,
                        NOW.minus(1, ChronoUnit.DAYS), NOW.plus(10, ChronoUnit.DAYS))
                .totalQuantity(100)
                .build();
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 → CouponNotFoundException, 카운터 미증가")
    void issue_notFound_throws() {
        when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(MEMBER_ID, COUPON_ID))
                .isInstanceOf(CouponNotFoundException.class);
        verify(couponRepository, never()).incrementIssuedCount(any());
    }

    @Test
    @DisplayName("발급 불가 상태(SUSPENDED) → CouponNotIssuableException, 카운터 미증가")
    void issue_notIssuable_throws() {
        coupon.changeStatus(CouponStatus.SUSPENDED);
        when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.issue(MEMBER_ID, COUPON_ID))
                .isInstanceOf(CouponNotIssuableException.class);
        verify(couponRepository, never()).incrementIssuedCount(any());
    }

    @Test
    @DisplayName("이미 발급받음(사전 검사) → CouponAlreadyIssuedException, 카운터 미증가")
    void issue_alreadyIssued_throws() {
        when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(coupon));
        when(memberCouponRepository.existsByCoupon_IdAndMemberId(COUPON_ID, MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.issue(MEMBER_ID, COUPON_ID))
                .isInstanceOf(CouponAlreadyIssuedException.class);
        verify(couponRepository, never()).incrementIssuedCount(any());
    }

    @Test
    @DisplayName("소진(원자적 UPDATE 0행) → CouponSoldOutException, member_coupon 미생성")
    void issue_soldOut_throws() {
        when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(coupon));
        when(memberCouponRepository.existsByCoupon_IdAndMemberId(COUPON_ID, MEMBER_ID)).thenReturn(false);
        when(couponRepository.incrementIssuedCount(COUPON_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.issue(MEMBER_ID, COUPON_ID))
                .isInstanceOf(CouponSoldOutException.class);
        verify(memberCouponRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("사전 검사 후 SUSPENDED 경합(UPDATE 0행) → 재조회로 COUPON_NOT_ISSUABLE")
    void issue_suspendedDuringRace_returnsNotIssuable() {
        Coupon suspended = Coupon.builder("정액 3천원", CouponDiscountType.FIXED_AMOUNT, 3_000,
                        NOW.minus(1, ChronoUnit.DAYS), NOW.plus(10, ChronoUnit.DAYS))
                .totalQuantity(100)
                .build();
        suspended.changeStatus(CouponStatus.SUSPENDED);
        // 1차 findById(사전검사)=ACTIVE 통과, increment 0행, 2차 findById(재조회)=SUSPENDED.
        when(couponRepository.findById(COUPON_ID))
                .thenReturn(Optional.of(coupon))
                .thenReturn(Optional.of(suspended));
        when(memberCouponRepository.existsByCoupon_IdAndMemberId(COUPON_ID, MEMBER_ID)).thenReturn(false);
        when(couponRepository.incrementIssuedCount(COUPON_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.issue(MEMBER_ID, COUPON_ID))
                .isInstanceOf(CouponNotIssuableException.class);
        verify(memberCouponRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("발급 성공 → 카운터 증가 + member_coupon 영속 + ISSUED 응답")
    void issue_success() {
        when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(coupon));
        when(memberCouponRepository.existsByCoupon_IdAndMemberId(COUPON_ID, MEMBER_ID)).thenReturn(false);
        when(couponRepository.incrementIssuedCount(COUPON_ID)).thenReturn(1);
        when(couponRepository.getReferenceById(COUPON_ID)).thenReturn(coupon);
        when(memberCouponRepository.saveAndFlush(any()))
                .thenReturn(MemberCoupon.issue(coupon, MEMBER_ID, NOW));

        MemberCouponResponse response = service.issue(MEMBER_ID, COUPON_ID);

        assertThat(response.status()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(response.name()).isEqualTo("정액 3천원");
        verify(couponRepository).incrementIssuedCount(COUPON_ID);
        verify(memberCouponRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("사전 검사 통과 후 UNIQUE 경합(동시 중복) → CouponAlreadyIssuedException")
    void issue_uniqueRace_mapsToAlreadyIssued() {
        when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(coupon));
        when(memberCouponRepository.existsByCoupon_IdAndMemberId(COUPON_ID, MEMBER_ID)).thenReturn(false);
        when(couponRepository.incrementIssuedCount(COUPON_ID)).thenReturn(1);
        when(couponRepository.getReferenceById(COUPON_ID)).thenReturn(coupon);
        when(memberCouponRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_member_coupon_coupon_member"));

        assertThatThrownBy(() -> service.issue(MEMBER_ID, COUPON_ID))
                .isInstanceOf(CouponAlreadyIssuedException.class);
    }

    @Test
    @DisplayName("탈퇴(soft delete) 회원 → MemberNotFoundException, 쿠폰 미조회·미증가 (#187)")
    void issue_memberWithdrawn_throws() {
        long withdrawnMemberId = 99L;
        when(memberRepository.existsByIdAndDeletedAtIsNull(withdrawnMemberId)).thenReturn(false);

        assertThatThrownBy(() -> service.issue(withdrawnMemberId, COUPON_ID))
                .isInstanceOf(MemberNotFoundException.class);
        verifyNoInteractions(couponRepository, memberCouponRepository);
    }
}
