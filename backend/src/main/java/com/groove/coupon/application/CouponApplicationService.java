package com.groove.coupon.application;

import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.domain.MemberCouponStatus;
import com.groove.coupon.exception.CouponAlreadyUsedException;
import com.groove.coupon.exception.CouponExpiredException;
import com.groove.coupon.exception.CouponNotFoundException;
import com.groove.coupon.exception.CouponNotIssuableException;
import com.groove.coupon.exception.CouponNotOwnedException;
import com.groove.order.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * 회원 쿠폰을 주문에 적용/복원하는 조율 서비스.
 *
 * 호출자는 둘 — OrderService.place 가 적용(applyToOrder), OrderService.cancel + AdminOrderService.refund 가
 * 복원(restoreForOrder)한다.
 *
 * 트랜잭션 경계: 두 진입점 모두 Propagation.MANDATORY — 호출자의 @Transactional 안에서 실행된다.
 *
 * 동시성: applyToOrder 는 findByIdForUpdate 로 회원 쿠폰 행을 잠근다(두 번째는 status=USED 를 본다).
 * restoreForOrder 는 단일 row 변경이라 추가 락이 없다.
 */
@Service
public class CouponApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CouponApplicationService.class);

    private final MemberCouponRepository memberCouponRepository;
    private final Clock clock;

    public CouponApplicationService(MemberCouponRepository memberCouponRepository, Clock clock) {
        this.memberCouponRepository = memberCouponRepository;
        this.clock = clock;
    }

    /**
     * 회원 쿠폰을 주문에 적용한다 — 검증 → 할인 계산 → Order.applyDiscount → use(orderId). 적용된 할인 금액을 반환한다.
     *
     * 검증은 fail-fast: 존재(404) → 소유자(403) → ISSUED 상태(USED/EXPIRED/CANCELLED 별 매핑) → 만료(422) →
     * Coupon.calculateDiscount → order.applyDiscount → memberCoupon.use(orderId)(ISSUED→USED).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long applyToOrder(Long memberCouponId, Long memberId, Order order) {
        if (memberCouponId == null) {
            throw new IllegalArgumentException("memberCouponId must not be null");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("memberId must not be null");
        }
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("order must be persisted before coupon application");
        }

        MemberCoupon memberCoupon = memberCouponRepository.findByIdForUpdate(memberCouponId)
                .orElseThrow(() -> new CouponNotFoundException(memberCouponId));

        if (!memberCoupon.getMemberId().equals(memberId)) {
            throw new CouponNotOwnedException(memberCouponId);
        }

        MemberCouponStatus status = memberCoupon.getStatus();
        if (status != MemberCouponStatus.ISSUED) {
            throw mapNonIssuableState(memberCouponId, memberCoupon, status);
        }

        Instant now = clock.instant();
        if (memberCoupon.getExpiresAt().isBefore(now)) {
            throw new CouponExpiredException(memberCouponId, memberCoupon.getExpiresAt());
        }

        long discount = memberCoupon.getCoupon().calculateDiscount(order.getTotalAmount());
        order.applyDiscount(discount);
        memberCoupon.use(order.getId());
        return discount;
    }

    /**
     * 주문에 적용된 쿠폰을 복원한다 — USED → ISSUED (복원 시점에 이미 만료됐으면 USED → EXPIRED). 쿠폰 미적용 주문은 no-op.
     *
     * MemberCoupon.restore(now) 가 usedAt 과 orderId 를 비우고 clock 기준 만료 여부를 판정한다.
     * status≠USED 가 관측되면 WARN 기록 후 복원을 스킵한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreForOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        memberCouponRepository.findByOrderId(orderId).ifPresent(memberCoupon -> {
            if (memberCoupon.getStatus() == MemberCouponStatus.USED) {
                memberCoupon.restore(clock.instant());
            } else {
                log.warn("쿠폰 복원 스킵 — status 가 USED 가 아님: orderId={}, memberCouponId={}, status={}",
                        orderId, memberCoupon.getId(), memberCoupon.getStatus());
            }
        });
    }

    /**
     * 주문에 적용된 쿠폰의 최소주문금액. 쿠폰 미적용 주문은 빈 값.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OptionalLong appliedCouponMinOrderAmount(Long orderId) {
        if (orderId == null) {
            return OptionalLong.empty();
        }
        return memberCouponRepository.findByOrderId(orderId)
                .map(memberCoupon -> OptionalLong.of(memberCoupon.getCoupon().getMinOrderAmount()))
                .orElseGet(OptionalLong::empty);
    }

    private RuntimeException mapNonIssuableState(Long memberCouponId, MemberCoupon memberCoupon,
                                                 MemberCouponStatus status) {
        return switch (status) {
            case USED -> new CouponAlreadyUsedException(memberCouponId);
            case EXPIRED -> new CouponExpiredException(memberCouponId, memberCoupon.getExpiresAt());
            case CANCELLED -> new CouponNotIssuableException(memberCouponId);
            // ISSUED 는 위에서 통과시켰음 — 컴파일러 만족용
            case ISSUED -> new IllegalStateException("unreachable");
        };
    }
}
