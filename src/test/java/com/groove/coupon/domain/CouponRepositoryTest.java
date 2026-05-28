package com.groove.coupon.domain;

import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.support.TestcontainersConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponRepository 통합 테스트 (Testcontainers MySQL). V14 스키마 ↔ 엔티티 매핑을 실제 INSERT/SELECT 로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("CouponRepository 통합 테스트 (Testcontainers MySQL)")
class CouponRepositoryTest {

    private static final Instant VALID_FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant VALID_UNTIL = VALID_FROM.plus(30, ChronoUnit.DAYS);

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("정액 쿠폰 영속 후 재로딩 — nullable(max_discount, total_quantity) NULL 매핑, 상태 ACTIVE")
    void persist_fixedAmount_roundTrip() {
        Coupon saved = couponRepository.save(Coupon.builder(
                        "정액 3천원", CouponDiscountType.FIXED_AMOUNT, 3_000, VALID_FROM, VALID_UNTIL)
                .build());
        Long id = saved.getId();
        flushAndClear();

        Coupon found = couponRepository.findById(id).orElseThrow();

        assertThat(found.getDiscountType()).isEqualTo(CouponDiscountType.FIXED_AMOUNT);
        assertThat(found.getDiscountValue()).isEqualTo(3_000L);
        assertThat(found.getMaxDiscountAmount()).isNull();
        assertThat(found.getTotalQuantity()).isNull();
        assertThat(found.getIssuedCount()).isZero();
        assertThat(found.getStatus()).isEqualTo(CouponStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("정률 쿠폰 영속 후 재로딩 — 상한·한정수량·enum 문자열 매핑")
    void persist_percentage_roundTrip() {
        Coupon saved = couponRepository.save(Coupon.builder(
                        "10% (최대 5천)", CouponDiscountType.PERCENTAGE, 10, VALID_FROM, VALID_UNTIL)
                .maxDiscountAmount(5_000L)
                .minOrderAmount(30_000)
                .totalQuantity(100)
                .build());
        Long id = saved.getId();
        flushAndClear();

        Coupon found = couponRepository.findById(id).orElseThrow();

        assertThat(found.getDiscountType()).isEqualTo(CouponDiscountType.PERCENTAGE);
        assertThat(found.getMaxDiscountAmount()).isEqualTo(5_000L);
        assertThat(found.getMinOrderAmount()).isEqualTo(30_000L);
        assertThat(found.getTotalQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("incrementIssuedCount — 한정수량 미만이면 1행 영향 + issued_count 증가")
    void incrementIssuedCount_belowLimit_increments() {
        Coupon saved = couponRepository.saveAndFlush(limited(2));

        int affected = couponRepository.incrementIssuedCount(saved.getId());
        flushAndClear();

        assertThat(affected).isEqualTo(1);
        assertThat(couponRepository.findById(saved.getId()).orElseThrow().getIssuedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("incrementIssuedCount — 소진(issued == total)이면 0행 영향 + 카운터 불변")
    void incrementIssuedCount_soldOut_returnsZero() {
        Coupon saved = couponRepository.saveAndFlush(limited(1));
        assertThat(couponRepository.incrementIssuedCount(saved.getId())).isEqualTo(1);
        flushAndClear();

        int affected = couponRepository.incrementIssuedCount(saved.getId());
        flushAndClear();

        assertThat(affected).isZero();
        assertThat(couponRepository.findById(saved.getId()).orElseThrow().getIssuedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("incrementIssuedCount — 무제한(total_quantity NULL)이면 항상 1행 영향")
    void incrementIssuedCount_unlimited_alwaysIncrements() {
        Coupon saved = couponRepository.saveAndFlush(Coupon.builder(
                "무제한", CouponDiscountType.FIXED_AMOUNT, 1_000, VALID_FROM, VALID_UNTIL).build());

        assertThat(couponRepository.incrementIssuedCount(saved.getId())).isEqualTo(1);
        assertThat(couponRepository.incrementIssuedCount(saved.getId())).isEqualTo(1);
        flushAndClear();

        assertThat(couponRepository.findById(saved.getId()).orElseThrow().getIssuedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByIdForUpdate — 비관적 락으로 행 로딩 (존재하면 반환)")
    void findByIdForUpdate_returnsCoupon() {
        Coupon saved = couponRepository.saveAndFlush(limited(5));
        flushAndClear();

        assertThat(couponRepository.findByIdForUpdate(saved.getId()))
                .get()
                .extracting(Coupon::getId)
                .isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("findIssuable — ACTIVE + 기간 내 쿠폰만, SUSPENDED·기간 밖 제외")
    void findIssuable_filtersByStatusAndPeriod() {
        Coupon active = couponRepository.save(limited(10));
        Coupon suspended = couponRepository.save(limited(10));
        suspended.changeStatus(CouponStatus.SUSPENDED);
        Coupon future = couponRepository.save(Coupon.builder(
                        "미래쿠폰", CouponDiscountType.FIXED_AMOUNT, 1_000,
                        VALID_UNTIL.plus(1, ChronoUnit.DAYS), VALID_UNTIL.plus(60, ChronoUnit.DAYS))
                .build());
        couponRepository.flush();
        flushAndClear();

        Instant now = VALID_FROM.plus(1, ChronoUnit.DAYS);
        Page<Coupon> page = couponRepository.findIssuable(now, PageRequest.of(0, 20));

        List<Long> ids = page.getContent().stream().map(Coupon::getId).toList();
        assertThat(ids).containsExactly(active.getId())
                .doesNotContain(suspended.getId(), future.getId());
    }

    private static Coupon limited(int totalQuantity) {
        return Coupon.builder("한정", CouponDiscountType.FIXED_AMOUNT, 1_000, VALID_FROM, VALID_UNTIL)
                .totalQuantity(totalQuantity)
                .build();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
