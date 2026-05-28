package com.groove.coupon.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 회원 보유 쿠폰 저장소.
 *
 * <p>회원당 1장은 {@code UNIQUE(coupon_id, member_id)} 가 DB 에서 강제하지만, 발급 경로(#90)는
 * 명시적 사전 검사로 {@code COUPON_ALREADY_ISSUED} 를 먼저 응답하기 위해
 * {@link #existsByCoupon_IdAndMemberId(Long, Long)} 를 쓴다.
 *
 * <p>목록 조회({@code GET /me/coupons})는 응답에 정책 필드(이름·할인규칙)가 필요하므로
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
}
