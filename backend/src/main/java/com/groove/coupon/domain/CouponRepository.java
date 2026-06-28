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

/** 선착순 발급 동시성 제어 — incrementIssuedCount(원자적 조건부 UPDATE)·findByIdForUpdate(비관적 락). */
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * 원자적 조건부 발급 카운터 증가 — 가능 조건일 때만 +1, 반환 1=확보 / 0=발급 불가(사유 판별은 서비스 재조회).
     * 기간 조건을 Coupon.isIssuable 과 일치시켜 만료 경계 TOCTOU 를 닫는다.
     * 벌크 UPDATE 는 컨텍스트를 우회하므로 flush(직전 dirty)·clear(1차 캐시) 자동.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.issuedCount = c.issuedCount + 1 "
            + "WHERE c.id = :id AND c.status = com.groove.coupon.domain.CouponStatus.ACTIVE "
            + "AND (c.totalQuantity IS NULL OR c.issuedCount < c.totalQuantity) "
            + "AND c.validFrom <= :now AND c.validUntil >= :now")
    int incrementIssuedCount(@Param("id") Long id, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

    /** ACTIVE + 발급 기간 내 쿠폰. 소진 여부는 필터하지 않는다. */
    @Query("SELECT c FROM Coupon c WHERE c.status = com.groove.coupon.domain.CouponStatus.ACTIVE "
            + "AND c.validFrom <= :now AND c.validUntil >= :now")
    Page<Coupon> findIssuable(@Param("now") Instant now, Pageable pageable);

    Page<Coupon> findByStatus(CouponStatus status, Pageable pageable);
}
