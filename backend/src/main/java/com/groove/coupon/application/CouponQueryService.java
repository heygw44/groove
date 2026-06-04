package com.groove.coupon.application;

import com.groove.coupon.api.dto.CouponResponse;
import com.groove.coupon.api.dto.MemberCouponResponse;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.domain.MemberCouponStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 쿠폰 조회 — 발급 가능 목록({@code GET /coupons})과 내 보유 쿠폰({@code GET /me/coupons}) (API.md §3.9).
 *
 * <p>발급/동시성은 {@link CouponIssueService} 가 담당하고, 본 서비스는 읽기 전용 조회만 다룬다.
 */
@Service
public class CouponQueryService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final Clock clock;

    public CouponQueryService(CouponRepository couponRepository,
                              MemberCouponRepository memberCouponRepository,
                              Clock clock) {
        this.couponRepository = couponRepository;
        this.memberCouponRepository = memberCouponRepository;
        this.clock = clock;
    }

    /** 발급 가능한 쿠폰(ACTIVE + 발급 기간 내) 페이지. 소진 여부는 {@code remainingQuantity} 로 노출한다. */
    @Transactional(readOnly = true)
    public Page<CouponResponse> listIssuable(Pageable pageable) {
        return couponRepository.findIssuable(clock.instant(), pageable).map(CouponResponse::from);
    }

    /** 회원 보유 쿠폰 페이지 — {@code status} 가 null 이면 전체, 아니면 해당 상태만. */
    @Transactional(readOnly = true)
    public Page<MemberCouponResponse> listForMember(Long memberId, MemberCouponStatus status, Pageable pageable) {
        Page<MemberCoupon> page = status == null
                ? memberCouponRepository.findByMemberId(memberId, pageable)
                : memberCouponRepository.findByMemberIdAndStatus(memberId, status, pageable);
        return page.map(MemberCouponResponse::from);
    }
}
