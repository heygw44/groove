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
 * 회원 보유 쿠폰 저장소.
 *
 * <p>회원당 1장은 {@code UNIQUE(coupon_id, member_id)} 가 DB 에서 강제하지만, 발급 경로(#90)는
 * 명시적 사전 검사로 {@code COUPON_ALREADY_ISSUED} 를 먼저 응답하기 위해
 * {@link #existsByCoupon_IdAndMemberId(Long, Long)} 를 쓴다.
 *
 * <p>목록 조회({@code GET /members/me/coupons})는 응답에 정책 필드(이름·할인규칙)가 필요하므로
 * {@link EntityGraph} 로 {@code coupon} 을 함께 fetch 해 N+1 을 피한다.
 *
 * <p>주문 적용 (#91) 은 {@link #findByIdForUpdate(Long)} 행 락으로 동일 회원의 동시 적용을 직렬화한다 —
 * {@code PaymentRepository.findByOrderIdForUpdate} 와 같은 패턴이며 LAZY 한 {@code coupon} 을
 * 즉시 fetch 해 {@code calculateDiscount} 호출 시 초기화 비용을 줄인다. 취소·환불 복원 (#91) 은
 * {@link #findByOrderId(Long)} 단순 조회면 충분하다 — Order 상태 머신이 동시 cancel/refund 를 막는다.
 */
public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    boolean existsByCoupon_IdAndMemberId(Long couponId, Long memberId);

    @EntityGraph(attributePaths = "coupon")
    Page<MemberCoupon> findByMemberId(Long memberId, Pageable pageable);

    @EntityGraph(attributePaths = "coupon")
    Page<MemberCoupon> findByMemberIdAndStatus(Long memberId, MemberCouponStatus status, Pageable pageable);

    /**
     * 주문 적용용 행 락 조회. {@code SELECT ... FOR UPDATE} 로 잠가 같은 회원이 동일 쿠폰을 동시에
     * 두 주문에 적용하려는 race 를 직렬화한다 (선례: {@code PaymentRepository.findByOrderIdForUpdate}).
     * 두 번째 트랜잭션은 첫 번째 커밋 후 행을 다시 읽으며 {@code status != ISSUED} 를 만나 거부된다.
     *
     * <p>JOIN FETCH 로 {@link MemberCoupon#getCoupon()} 을 함께 가져와 {@code calculateDiscount}
     * 호출 시 LAZY 초기화 추가 쿼리를 피한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mc FROM MemberCoupon mc JOIN FETCH mc.coupon WHERE mc.id = :id")
    Optional<MemberCoupon> findByIdForUpdate(@Param("id") Long id);

    /**
     * 주문 취소/환불 복원용 — 해당 주문에 적용된 회원 쿠폰을 찾는다. 주문당 최대 1장이라 단건. 미적용
     * 주문은 빈 결과 → 호출자가 no-op 로 처리한다.
     */
    Optional<MemberCoupon> findByOrderId(Long orderId);

    /**
     * 만료 배치 (#92) — {@code expires_at < now} 인 {@code ISSUED} 행을 {@code EXPIRED} 로 일괄 전환한다.
     * 반환값은 영향받은 행 수이며, 호출자가 배치 크기와 같으면 다음 배치를 이어 처리한다.
     *
     * <p>{@code IdempotencyRecordRepository.deleteExpiredBatch} 와 동일하게 MySQL 네이티브
     * {@code LIMIT} 으로 한 트랜잭션의 락 범위를 제한한다. USED/CANCELLED/EXPIRED 는 WHERE 조건에서
     * 자동 제외된다. 벌크 UPDATE 는 auditing 을 우회하므로 {@code updated_at} 도 같이 갱신한다.
     *
     * <p><b>호출 규약</b>: 호출자가 트랜잭션을 끊어 락 범위를 제한해야 한다 — 표준 경로는
     * {@code MemberCouponExpirationTask} 의 REQUIRES_NEW 템플릿. 본 메서드에 {@code @Transactional}
     * 을 부착해 호출자가 트랜잭션 없이 부르는 회귀에도 새 트랜잭션이 시작되도록 보호한다.
     *
     * <p><b>유지보수 주의</b>: 'ISSUED'/'EXPIRED' enum 이름이 SQL 리터럴로 박혀 있어
     * {@link MemberCouponStatus} 리네이밍 시 컴파일러가 잡지 못한다 — 본 메서드를 손대는 PR 은
     * 단위 테스트로 enum.name() 값 가정을 함께 검증한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "UPDATE member_coupon "
            + "SET status = 'EXPIRED', updated_at = :now "
            + "WHERE status = 'ISSUED' AND expires_at < :now "
            + "LIMIT :batchSize", nativeQuery = true)
    int expireOverdueBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
