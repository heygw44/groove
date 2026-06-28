package com.groove.coupon.application;

import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.domain.MemberCouponStatus;
import com.groove.coupon.exception.CouponAlreadyUsedException;
import com.groove.coupon.exception.CouponExpiredException;
import com.groove.coupon.exception.CouponNotFoundException;
import com.groove.coupon.exception.CouponNotIssuableException;
import com.groove.coupon.exception.CouponNotOwnedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * 회원 쿠폰을 주문에 적용/복원하는 조율 서비스. 모든 진입점 Propagation.MANDATORY — 호출자 트랜잭션 안에서 실행.
 * applyToOrder 는 findByIdForUpdate 로 쿠폰 행을 잠가 동시 적용을 직렬화한다.
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
     * 쿠폰을 적용하고 할인액을 반환. 검증 순서: 존재 → 소유자 → ISSUED → 만료 → 할인 계산 → use.
     * Order 엔티티는 참조하지 않고 총액만 받는다(슬라이스 단방향). 반영은 호출자 책임.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long applyToOrder(Long memberCouponId, Long memberId, Long orderId, long orderTotalAmount) {
        if (memberCouponId == null) {
            throw new IllegalArgumentException("memberCouponId must not be null");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("memberId must not be null");
        }
        if (orderId == null) {
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

        long discount = memberCoupon.getCoupon().calculateDiscount(orderTotalAmount);
        memberCoupon.use(orderId, now);
        return discount;
    }

    /** 쿠폰 복원(USED → ISSUED). restore 가 멱등이라 무락 동시 복원에 안전. 만료된 쿠폰은 grace 만큼 연장해 되살린다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreForOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        memberCouponRepository.findByOrderId(orderId)
                .ifPresent(memberCoupon -> memberCoupon.restore(clock.instant(), restoreGrace));
    }

    /** 주문에 적용된 쿠폰의 최소주문금액. 쿠폰 미적용 주문은 빈 값. */
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
            case ISSUED -> new IllegalStateException("unreachable"); // 위에서 통과
        };
    }
}
