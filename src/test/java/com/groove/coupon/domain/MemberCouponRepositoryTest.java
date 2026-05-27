package com.groove.coupon.domain;

import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.support.TestcontainersConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MemberCouponRepository 통합 테스트 (Testcontainers MySQL).
 *
 * <p>핵심: 회원당 1장을 보증하는 {@code UNIQUE(coupon_id, member_id)} 가 DB 레벨에서 강제되는지
 * (도메인으로는 검증 불가) 를 중복 INSERT 로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("MemberCouponRepository 통합 테스트 (Testcontainers MySQL)")
class MemberCouponRepositoryTest {

    private static final Instant VALID_FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant VALID_UNTIL = VALID_FROM.plus(30, ChronoUnit.DAYS);
    private static final long OTHER_MEMBER_ID = 9_999L;

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager em;

    private Long memberId;
    private Coupon coupon;

    @BeforeEach
    void setUp() {
        // member_coupon.member_id → member.id, coupon_id → coupon.id FK 때문에 둘 다 선행 존재해야 한다.
        Member member = memberRepository.saveAndFlush(
                Member.register("coupon-repo-test@example.com", "$2a$12$hash", "쿠폰사용자", "01012345678"));
        memberId = member.getId();
        coupon = couponRepository.saveAndFlush(Coupon.builder(
                        "정액 3천원", CouponDiscountType.FIXED_AMOUNT, 3_000, VALID_FROM, VALID_UNTIL)
                .totalQuantity(100)
                .build());
    }

    @Test
    @DisplayName("발급 후 재로딩 — 상태 ISSUED, coupon 연관·expiresAt 매핑")
    void issue_roundTrip() {
        MemberCoupon saved = memberCouponRepository.save(MemberCoupon.issue(coupon, memberId));
        Long id = saved.getId();
        em.flush();
        em.clear();

        MemberCoupon found = memberCouponRepository.findById(id).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(found.getMemberId()).isEqualTo(memberId);
        assertThat(found.getCoupon().getId()).isEqualTo(coupon.getId());
        assertThat(found.getExpiresAt()).isEqualTo(VALID_UNTIL);
        assertThat(found.getUsedAt()).isNull();
        assertThat(found.getOrderId()).isNull();
    }

    @Test
    @DisplayName("existsByCoupon_IdAndMemberId — 보유 회원 true, 미보유 회원 false")
    void existsByCouponAndMember() {
        memberCouponRepository.saveAndFlush(MemberCoupon.issue(coupon, memberId));

        assertThat(memberCouponRepository.existsByCoupon_IdAndMemberId(coupon.getId(), memberId)).isTrue();
        assertThat(memberCouponRepository.existsByCoupon_IdAndMemberId(coupon.getId(), OTHER_MEMBER_ID)).isFalse();
    }

    @Test
    @DisplayName("회원당 동일 쿠폰 2장 발급 시도 → UNIQUE(coupon_id, member_id) 위반")
    void duplicateIssue_violatesUnique() {
        memberCouponRepository.saveAndFlush(MemberCoupon.issue(coupon, memberId));

        assertThatThrownBy(() ->
                memberCouponRepository.saveAndFlush(MemberCoupon.issue(coupon, memberId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByMemberId — 회원 보유 전체를 coupon 함께 fetch (EntityGraph N+1 방지)")
    void findByMemberId_fetchesCoupon() {
        Coupon another = couponRepository.saveAndFlush(Coupon.builder(
                "정률 10%", CouponDiscountType.PERCENTAGE, 10, VALID_FROM, VALID_UNTIL).build());
        memberCouponRepository.save(MemberCoupon.issue(coupon, memberId));
        memberCouponRepository.save(MemberCoupon.issue(another, memberId));
        em.flush();
        em.clear();

        Page<MemberCoupon> page = memberCouponRepository.findByMemberId(
                memberId, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "issuedAt")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allSatisfy(mc -> assertThat(mc.getCoupon().getName()).isNotBlank());
    }

    @Test
    @DisplayName("findByMemberIdAndStatus — 상태로 필터 (ISSUED 1장, CANCELLED 1장)")
    void findByMemberIdAndStatus_filtersByStatus() {
        Coupon another = couponRepository.saveAndFlush(Coupon.builder(
                "정률 10%", CouponDiscountType.PERCENTAGE, 10, VALID_FROM, VALID_UNTIL).build());
        memberCouponRepository.save(MemberCoupon.issue(coupon, memberId));
        MemberCoupon cancelled = MemberCoupon.issue(another, memberId);
        cancelled.cancel();
        memberCouponRepository.save(cancelled);
        em.flush();
        em.clear();

        var pageable = PageRequest.of(0, 20);
        assertThat(memberCouponRepository.findByMemberIdAndStatus(memberId, MemberCouponStatus.ISSUED, pageable)
                .getTotalElements()).isEqualTo(1);
        assertThat(memberCouponRepository.findByMemberIdAndStatus(memberId, MemberCouponStatus.CANCELLED, pageable)
                .getTotalElements()).isEqualTo(1);
    }
}
