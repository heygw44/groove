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
 * 쿠폰 정책 저장소 — 선착순 발급의 동시성 제어를 제공한다.
 * incrementIssuedCount(원자적 조건부 UPDATE)·findByIdForUpdate(비관적 락). 베이스라인(락 없음)은 기본 findById.
 */
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * 원자적 조건부 발급 카운터 증가 — ACTIVE + 발급 기간(validFrom ≤ now ≤ validUntil) + issued_count < total_quantity
     * (또는 무제한)일 때만 +1. 반환 1=슬롯 확보, 0=발급 불가(사유 판별은 서비스가 재조회).
     * 기간 조건은 경계 포함으로 Coupon.isIssuable 과 일치시켜 만료 경계 TOCTOU 를 닫는다.
     * 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로 flushAutomatically(직전 dirty flush)·clearAutomatically(1차 캐시 비움).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.issuedCount = c.issuedCount + 1 "
            + "WHERE c.id = :id AND c.status = com.groove.coupon.domain.CouponStatus.ACTIVE "
            + "AND (c.totalQuantity IS NULL OR c.issuedCount < c.totalQuantity) "
            + "AND c.validFrom <= :now AND c.validUntil >= :now")
    int incrementIssuedCount(@Param("id") Long id, @Param("now") Instant now);

    /** coupon 행을 SELECT ... FOR UPDATE 로 잠근다. 행 락은 트랜잭션 종료 시 해제된다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

    /**
     * 발급 가능한 쿠폰 목록 — status = ACTIVE 이고 현재가 발급 기간(validFrom ≤ now ≤ validUntil) 안인 쿠폰.
     * 소진 여부는 필터하지 않는다.
     */
    @Query("SELECT c FROM Coupon c WHERE c.status = com.groove.coupon.domain.CouponStatus.ACTIVE "
            + "AND c.validFrom <= :now AND c.validUntil >= :now")
    Page<Coupon> findIssuable(@Param("now") Instant now, Pageable pageable);

    /** 관리자 목록 조회(상태 필터). */
    Page<Coupon> findByStatus(CouponStatus status, Pageable pageable);
}
