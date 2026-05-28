package com.groove.coupon.application;

import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.domain.MemberCouponStatus;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.support.CouponFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemberCouponExpirationTask 통합 테스트 (Testcontainers MySQL).
 *
 * <p>{@code expiration.batch-size=2} 로 띄워 배치 루프(여러 반복)와 만료 조건(ISSUED + 시각 경과)을 함께 검증한다.
 * 스케줄러 자동 실행은 test 프로파일에서 {@code expiration.cron: "-"} 로 꺼져 있으므로 태스크를 직접 호출한다.
 */
@SpringBootTest(properties = "groove.coupon.expiration.batch-size=2")
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("MemberCouponExpirationTask — 만료 회원 쿠폰 EXPIRED 일괄 전환")
class MemberCouponExpirationTaskTest {

    @Autowired
    private MemberCouponExpirationTask task;
    @Autowired
    private MemberCouponRepository memberCouponRepository;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Coupon coupon;
    private final List<Long> memberIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        memberCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        memberIds.clear();
        coupon = couponRepository.saveAndFlush(CouponFixtures.fixedAmount(null));
        // 회원당 1장 UNIQUE 제약 때문에 케이스마다 별도 회원이 필요하다 — 충분히 만들어 둔다.
        for (int i = 0; i < 10; i++) {
            Member m = memberRepository.saveAndFlush(
                    Member.register("m" + i + "@example.com", "$2a$10$dummy", "M" + i, "0100000000" + i));
            memberIds.add(m.getId());
        }
    }

    @Test
    @DisplayName("만료된 ISSUED 만 배치로 EXPIRED 전환, 유효한 ISSUED 는 보존")
    void expiresOnlyOverdue_inBatches() {
        // batch-size=2 → 만료 5건은 2+2+1+0 로 4회 배치
        for (int i = 0; i < 5; i++) {
            persist(CouponFixtures.issuedWithExpiry(coupon, memberIds.get(i), CouponFixtures.hoursAgo(1)));
        }
        Long valid1 = persist(CouponFixtures.issuedWithExpiry(coupon, memberIds.get(5), CouponFixtures.hoursLater(1))).getId();
        Long valid2 = persist(CouponFixtures.issuedWithExpiry(coupon, memberIds.get(6), CouponFixtures.hoursLater(24))).getId();

        int updated = task.expireOverdueAll(Instant.now());

        assertThat(updated).isEqualTo(5);
        assertThat(countByStatus(MemberCouponStatus.EXPIRED)).isEqualTo(5);
        assertThat(memberCouponRepository.findById(valid1).orElseThrow().getStatus())
                .isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(memberCouponRepository.findById(valid2).orElseThrow().getStatus())
                .isEqualTo(MemberCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("USED 는 만료 시각이 지났어도 전환되지 않음 — WHERE status=ISSUED 가 종착 상태를 보호")
    void usedStatus_isNotExpired() {
        // FK fk_member_coupon_order 가 실 주문을 요구하므로 orderId 는 null 로 두고 상태만 USED 로 강제.
        MemberCoupon used = persist(CouponFixtures.issuedWithExpiry(coupon, memberIds.get(0), CouponFixtures.hoursAgo(1)));
        ReflectionTestUtils.setField(used, "status", MemberCouponStatus.USED);
        memberCouponRepository.saveAndFlush(used);

        int updated = task.expireOverdueAll(Instant.now());

        assertThat(updated).isZero();
        assertThat(memberCouponRepository.findById(used.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberCouponStatus.USED);
    }

    @Test
    @DisplayName("CANCELLED 는 만료 시각이 지났어도 전환되지 않음")
    void cancelledStatus_isNotExpired() {
        MemberCoupon cancelled = persist(CouponFixtures.issuedWithExpiry(coupon, memberIds.get(0), CouponFixtures.hoursAgo(1)));
        cancelled.cancel();
        memberCouponRepository.saveAndFlush(cancelled);

        int updated = task.expireOverdueAll(Instant.now());

        assertThat(updated).isZero();
        assertThat(memberCouponRepository.findById(cancelled.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberCouponStatus.CANCELLED);
    }

    @Test
    @DisplayName("만료 대상이 없으면 0건")
    void nothingToExpire_returnsZero() {
        persist(CouponFixtures.issuedWithExpiry(coupon, memberIds.get(0), CouponFixtures.hoursLater(1)));

        int updated = task.expireOverdueAll(Instant.now());

        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("expireOverdue() — 스케줄 진입점도 예외 없이 배치 수행")
    void scheduledEntryPoint_runsBatch() {
        persist(CouponFixtures.issuedWithExpiry(coupon, memberIds.get(0), CouponFixtures.hoursAgo(1)));

        task.expireOverdue();

        assertThat(memberCouponRepository.findAll().get(0).getStatus())
                .isEqualTo(MemberCouponStatus.EXPIRED);
    }

    private MemberCoupon persist(MemberCoupon mc) {
        return memberCouponRepository.saveAndFlush(mc);
    }

    private long countByStatus(MemberCouponStatus status) {
        return memberCouponRepository.findAll().stream()
                .filter(mc -> mc.getStatus() == status)
                .count();
    }
}
