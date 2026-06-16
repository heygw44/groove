package com.groove.coupon.application;

import com.groove.coupon.api.dto.CouponResponse;
import com.groove.coupon.api.dto.MemberCouponResponse;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.domain.MemberCouponStatus;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderRepository.OrderNumberView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 쿠폰 조회 — 발급 가능 목록(GET /coupons)과 내 보유 쿠폰(GET /members/me/coupons).
 */
@Service
public class CouponQueryService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public CouponQueryService(CouponRepository couponRepository,
                              MemberCouponRepository memberCouponRepository,
                              OrderRepository orderRepository,
                              Clock clock) {
        this.couponRepository = couponRepository;
        this.memberCouponRepository = memberCouponRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /** 발급 가능한 쿠폰(ACTIVE + 발급 기간 내) 페이지. 소진 여부는 remainingQuantity 로 노출한다. */
    @Transactional(readOnly = true)
    public Page<CouponResponse> listIssuable(Pageable pageable) {
        return couponRepository.findIssuable(clock.instant(), pageable).map(CouponResponse::from);
    }

    /**
     * 회원 보유 쿠폰 페이지 — status 가 null 이면 전체, 아니면 해당 상태만.
     *
     * <p>USED 쿠폰의 사용 주문번호(orderNumber)는 페이지의 orderId 집합을 모아 OrderRepository.findByIdIn 으로
     * 한 번에 resolve 한다. 복원된 쿠폰은 orderId 가 비어 있어 null 로 매핑된다.
     */
    @Transactional(readOnly = true)
    public Page<MemberCouponResponse> listForMember(Long memberId, MemberCouponStatus status, Pageable pageable) {
        Page<MemberCoupon> page = status == null
                ? memberCouponRepository.findByMemberId(memberId, pageable)
                : memberCouponRepository.findByMemberIdAndStatus(memberId, status, pageable);

        Set<Long> orderIds = page.getContent().stream()
                .map(MemberCoupon::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // emptyMap()(Map.of() 아님): ISSUED 쿠폰은 orderId 가 null 이라 get(null) 호출이 필요한데,
        // Map.of() 의 불변 맵은 null 키 조회 시 NPE 를 던진다.
        Map<Long, String> orderNumbers = orderIds.isEmpty()
                ? Collections.emptyMap()
                : orderRepository.findByIdIn(orderIds).stream()
                        .collect(Collectors.toMap(OrderNumberView::getId, OrderNumberView::getOrderNumber));

        return page.map(mc -> MemberCouponResponse.from(mc, orderNumbers.get(mc.getOrderId())));
    }
}
