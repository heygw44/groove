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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
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

    private final MemberCouponRepository memberCouponRepository;
    private final Clock clock;
    private final Duration restoreGrace;

    public CouponApplicationService(MemberCouponRepository memberCouponRepository, Clock clock,
                                    CouponRestoreProperties restoreProperties) {
        this.memberCouponRepository = memberCouponRepository;
        this.clock = clock;
        this.restoreGrace = restoreProperties.grace();
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
        memberCoupon.use(order.getId(), now);
        return discount;
    }

    /**
     * 주문에 적용된 쿠폰을 복원한다 — USED → ISSUED. 쿠폰 미적용 주문은 no-op.
     *
     * MemberCoupon.restore(now, grace) 가 멱등이라 USED 가 아니면 no-op 이며(무락 동시 복원 안전),
     * 복원 시점에 이미 만료된 쿠폰은 grace 만큼 연장해 되살린다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreForOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        memberCouponRepository.findByOrderId(orderId)
                .ifPresent(memberCoupon -> memberCoupon.restore(clock.instant(), restoreGrace));
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
