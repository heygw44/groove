package com.groove.coupon.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 쿠폰 정책 저장소.
 *
 * <p>선착순 발급의 동시성 제어를 3단계로 제공한다 (설계 §4, decisions/coupon-concurrency.md):
 * <ul>
 *   <li>{@link #incrementIssuedCount(Long)} — <b>최종(프로덕션)</b>. 원자적 조건부 UPDATE 로 한 문장에
 *       소진 검사 + 카운터 증가를 수행한다 ({@code PaymentRepository} 의 락과 달리 행 락이 짧다).</li>
 *   <li>{@link #findByIdForUpdate(Long)} — 비관적 락 단계. {@code PaymentRepository.findByOrderIdForUpdate}
 *       패턴 재사용. 정확하나 직렬화로 처리량이 낮다.</li>
 * </ul>
 * 베이스라인(락 없음)은 기본 {@link #findById}로 구현하므로 별도 메서드가 없다.
 */
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * 원자적 조건부 발급 카운터 증가 — 정책이 {@code ACTIVE} 이고 {@code issued_count < total_quantity}
     * (또는 무제한) 인 경우에만 {@code +1} 한다. 반환값이 발급 가능 여부 신호다: {@code 1} = 슬롯 확보
     * 성공, {@code 0} = 발급 불가(소진 또는 비ACTIVE).
     *
     * <p>상태 검사를 WHERE 에 포함해 이 UPDATE 자체가 권위 있는 게이트키퍼가 되게 한다 — 사전 검사
     * (SELECT) 와 이 UPDATE 사이에 관리자가 쿠폰을 SUSPENDED 로 바꾸는 TOCTOU 경합에도 SUSPENDED 쿠폰이
     * 발급되지 않는다. 0행일 때 소진/비ACTIVE 사유 판별은 서비스가 재조회로 한다.
     *
     * <p>벌크 UPDATE 는 영속성 컨텍스트를 우회하므로 {@code flushAutomatically = true} 로 직전 dirty
     * 상태를 먼저 flush 하고, {@code clearAutomatically = true} 로 1차 캐시를 비워 같은 트랜잭션에서 이후
     * 로딩되는 {@link Coupon#getIssuedCount()} 가 stale 값을 보지 않게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.issuedCount = c.issuedCount + 1 "
            + "WHERE c.id = :id AND c.status = com.groove.coupon.domain.CouponStatus.ACTIVE "
            + "AND (c.totalQuantity IS NULL OR c.issuedCount < c.totalQuantity)")
    int incrementIssuedCount(@Param("id") Long id);

    /**
     * 비관적 락 발급 단계 전용 — coupon 행을 {@code SELECT ... FOR UPDATE} 로 잠근다
     * ({@code PaymentRepository.findByOrderIdForUpdate} 와 동일 패턴). 행 락은 트랜잭션 종료 시 해제된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

    /**
     * 발급 가능한 쿠폰 목록 (API.md §3.9 {@code GET /coupons}) — {@code status = ACTIVE} 이고 현재가
     * 발급 기간({@code validFrom ≤ now ≤ validUntil}) 안인 쿠폰. 소진 여부는 필터하지 않고
     * {@code remainingQuantity} 로 노출한다.
     */
    @Query("SELECT c FROM Coupon c WHERE c.status = com.groove.coupon.domain.CouponStatus.ACTIVE "
            + "AND c.validFrom <= :now AND c.validUntil >= :now")
    Page<Coupon> findIssuable(@Param("now") Instant now, Pageable pageable);
}
