package com.groove.coupon.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** 회원 보유 쿠폰 저장소. 주문 적용은 findByIdForUpdate 행 락으로 직렬화, 복원은 findByOrderId 단순 조회. */
public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    boolean existsByCoupon_IdAndMemberId(Long couponId, Long memberId);

    @EntityGraph(attributePaths = "coupon")
    Page<MemberCoupon> findByMemberId(Long memberId, Pageable pageable);

    @EntityGraph(attributePaths = "coupon")
    Page<MemberCoupon> findByMemberIdAndStatus(Long memberId, MemberCouponStatus status, Pageable pageable);

    /** FOR UPDATE 로 동시 적용 직렬화(두 번째는 status != ISSUED 를 만나 거부). coupon 을 JOIN FETCH. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mc FROM MemberCoupon mc JOIN FETCH mc.coupon WHERE mc.id = :id")
    Optional<MemberCoupon> findByIdForUpdate(@Param("id") Long id);

    /** 주문당 최대 1장. 미적용 주문은 빈 결과. */
    Optional<MemberCoupon> findByOrderId(Long orderId);

    /** 만료 배치. LIMIT 으로 락 범위 제한, 벌크 UPDATE 라 auditing 우회 → updated_at 직접 갱신. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "UPDATE member_coupon "
            + "SET status = 'EXPIRED', updated_at = :now "
            + "WHERE status = 'ISSUED' AND expires_at < :now "
            + "LIMIT :batchSize", nativeQuery = true)
    int expireOverdueBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
