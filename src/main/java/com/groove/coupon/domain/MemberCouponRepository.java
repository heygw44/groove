package com.groove.coupon.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 보유 쿠폰 저장소.
 *
 * <p>회원당 1장은 {@code UNIQUE(coupon_id, member_id)} 가 DB 에서 강제하지만, 발급 경로(#90)는
 * 명시적 사전 검사로 {@code COUPON_ALREADY_ISSUED} 를 먼저 응답하기 위해
 * {@link #existsByCoupon_IdAndMemberId(Long, Long)} 를 쓴다.
 *
 * <p>목록 조회({@code GET /me/coupons})는 응답에 정책 필드(이름·할인규칙)가 필요하므로
 * {@link EntityGraph} 로 {@code coupon} 을 함께 fetch 해 N+1 을 피한다.
 */
public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    boolean existsByCoupon_IdAndMemberId(Long couponId, Long memberId);

    @EntityGraph(attributePaths = "coupon")
    Page<MemberCoupon> findByMemberId(Long memberId, Pageable pageable);

    @EntityGraph(attributePaths = "coupon")
    Page<MemberCoupon> findByMemberIdAndStatus(Long memberId, MemberCouponStatus status, Pageable pageable);
}
