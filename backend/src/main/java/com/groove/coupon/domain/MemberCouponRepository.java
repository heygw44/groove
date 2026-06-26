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

/**
 * 회원 보유 쿠폰 저장소. 회원당 1장은 UNIQUE(coupon_id, member_id) 가 DB 에서 강제하고, 발급 경로는 사전 검사로
 * existsByCoupon_IdAndMemberId 를 쓴다. 목록 조회는 EntityGraph 로 coupon 을 함께 fetch 한다.
 * 주문 적용은 findByIdForUpdate 행 락으로 동시 적용을 직렬화하고, 취소·환불 복원은 findByOrderId 단순 조회.
 */
public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    boolean existsByCoupon_IdAndMemberId(Long couponId, Long memberId);

    @EntityGraph(attributePaths = "coupon")
    Page<MemberCoupon> findByMemberId(Long memberId, Pageable pageable);

    @EntityGraph(attributePaths = "coupon")
    Page<MemberCoupon> findByMemberIdAndStatus(Long memberId, MemberCouponStatus status, Pageable pageable);

    /**
     * 주문 적용용 행 락 조회 — FOR UPDATE 로 동시 적용 race 를 직렬화한다(두 번째는 status != ISSUED 를 만나 거부).
     * JOIN FETCH 로 coupon 을 함께 가져온다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mc FROM MemberCoupon mc JOIN FETCH mc.coupon WHERE mc.id = :id")
    Optional<MemberCoupon> findByIdForUpdate(@Param("id") Long id);

    /** 해당 주문에 적용된 회원 쿠폰을 찾는다. 주문당 최대 1장. 미적용 주문은 빈 결과. */
    Optional<MemberCoupon> findByOrderId(Long orderId);

    /**
     * 만료 배치 — expires_at < now 인 ISSUED 행을 EXPIRED 로 일괄 전환하고 영향 행 수를 반환한다.
     * 네이티브 LIMIT 으로 한 트랜잭션의 락 범위를 제한한다. 벌크 UPDATE 는 auditing 을 우회하므로 updated_at 도 같이 갱신한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "UPDATE member_coupon "
            + "SET status = 'EXPIRED', updated_at = :now "
            + "WHERE status = 'ISSUED' AND expires_at < :now "
            + "LIMIT :batchSize", nativeQuery = true)
    int expireOverdueBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
