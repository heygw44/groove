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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 쿠폰 조회 — 발급 가능 목록(GET /coupons)과 내 보유 쿠폰(GET /members/me/coupons). */
@Service
public class CouponQueryService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final OrderNumberLookup orderNumberLookup;
    private final Clock clock;

    public CouponQueryService(CouponRepository couponRepository,
                              MemberCouponRepository memberCouponRepository,
                              OrderNumberLookup orderNumberLookup,
                              Clock clock) {
        this.couponRepository = couponRepository;
        this.memberCouponRepository = memberCouponRepository;
        this.orderNumberLookup = orderNumberLookup;
        this.clock = clock;
    }

    /** 발급 가능한 쿠폰(ACTIVE + 발급 기간 내) 페이지. 소진 여부는 remainingQuantity 로 노출한다. */
    @Transactional(readOnly = true)
    public Page<CouponResponse> listIssuable(Pageable pageable) {
        return couponRepository.findIssuable(clock.instant(), pageable).map(CouponResponse::from);
    }

    /** USED 쿠폰의 orderNumber 는 orderId 집합을 모아 한 번에 resolve(N+1 회피, 복원된 쿠폰은 null). */
    @Transactional(readOnly = true)
    public Page<MemberCouponResponse> listForMember(Long memberId, MemberCouponStatus status, Pageable pageable) {
        Page<MemberCoupon> page = status == null
                ? memberCouponRepository.findByMemberId(memberId, pageable)
                : memberCouponRepository.findByMemberIdAndStatus(memberId, status, pageable);

        Set<Long> orderIds = page.getContent().stream()
                .map(MemberCoupon::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // ISSUED 쿠폰은 orderId 가 null 이라 get(null) 을 호출하므로, lookup 은 null 키를 허용하는 맵을 반환한다.
        Map<Long, String> orderNumbers = orderNumberLookup.orderNumbersByIds(orderIds);

        return page.map(mc -> MemberCouponResponse.from(mc, orderNumbers.get(mc.getOrderId())));
    }
}
