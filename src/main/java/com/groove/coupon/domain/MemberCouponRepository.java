package com.groove.coupon.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 보유 쿠폰 저장소.
 *
 * <p>회원당 1장은 {@code UNIQUE(coupon_id, member_id)} 가 DB 에서 강제하지만, 발급 경로(#90)는
 * 명시적 사전 검사로 {@code COUPON_ALREADY_ISSUED} 를 먼저 응답하기 위해
 * {@link #existsByCoupon_IdAndMemberId(Long, Long)} 를 쓴다.
 */
public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    boolean existsByCoupon_IdAndMemberId(Long couponId, Long memberId);
}
