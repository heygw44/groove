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

/**
 * 회원 쿠폰을 주문에 적용/복원하는 조율 서비스 (#91, docs/plans/coupon-system.md §5).
 *
 * 호출자는 둘 — OrderService.place 가 적용(applyToOrder), OrderService.cancel + AdminOrderService.refund 가
 * 복원(restoreForOrder)한다. 취소·환불 양 경로 모두 복원을 호출해야 한다(#91 DoD HIGH 리스크).
 *
 * 트랜잭션 경계: 두 진입점 모두 Propagation.MANDATORY — 호출자의 @Transactional 안에서 실행된다. 적용은 주문
 * 저장과 같은 트랜잭션이어야 MemberCoupon.use(orderId) 와 Order.discountAmount 가 함께 커밋된다.
 *
 * 동시성: applyToOrder 는 findByIdForUpdate 로 회원 쿠폰 행을 잠가 같은 쿠폰을 두 주문에 동시 적용하는 race 를
 * 막는다(두 번째는 status=USED 를 본다). restoreForOrder 는 단일 row 변경이라 추가 락이 불필요 — 주문 상태
 * 머신이 cancel(PENDING 한정)과 refund(PAID 한정)의 동시 진입을 막는다.
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
     * 회원 쿠폰을 주문에 적용한다 — 검증 → 할인 계산 → Order.applyDiscount → use(orderId).
     *
     * 호출 시점: orderRepository.save(order) 직후(orderId 부여 뒤). 같은 트랜잭션이라 MemberCoupon.use 의 dirty
     * 변경은 함께 커밋되고, 검증 실패는 트랜잭션 전체를 롤백해 재고 차감·주문 저장도 되돌린다.
     *
     * 검증은 저비용→고비용 fail-fast: 존재(404) → 소유자(403, 메시지에 memberId 노출 안 함) → ISSUED 상태(USED/
     * EXPIRED/CANCELLED 별 매핑) → 만료(422) → Coupon.calculateDiscount(min_order·정액/정률·상한·discount≤subtotal)
     * → order.applyDiscount(PENDING + 0≤discount≤total 가드) → memberCoupon.use(orderId)(ISSUED→USED).
     *
     * @return 적용된 할인 금액 — 호출자 로깅/검증용
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
            // memberId 를 메시지에 넣지 않는다 — 타 회원 쿠폰의 존재 누설 회피.
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
     * 호출 시점: OrderService.cancel / AdminOrderService.refund 의 재고 복원 직후. MemberCoupon.restore(now) 가
     * usedAt 과 orderId 를 비우고 clock 기준 만료 여부를 판정해 만료 쿠폰이 ISSUED 로 부활하지 않게 한다.
     *
     * findByOrderId 는 use(orderId) 가 USED 로 전이시킨 행만 반환하므로 정상 흐름에선 status==USED 가 보장된다.
     * 그럼에도 status≠USED 가 관측되면 외부 수정·만료 배치 race 등 데이터 이상 신호이므로 WARN 기록 후 복원을
     * 스킵한다 — restore() 가 던질 IllegalStateException 을 막으면서 무성공으로 사라지지 않게 한다.
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

    private RuntimeException mapNonIssuableState(Long memberCouponId, MemberCoupon memberCoupon,
                                                 MemberCouponStatus status) {
        return switch (status) {
            case USED -> new CouponAlreadyUsedException(memberCouponId);
            case EXPIRED -> new CouponExpiredException(memberCouponId, memberCoupon.getExpiresAt());
            // CANCELLED 는 발급 자체가 취소된 상태 — '발급 불가' 시맨틱이 가장 가깝다.
            case CANCELLED -> new CouponNotIssuableException(memberCouponId);
            // ISSUED 는 위에서 통과시켰음 — 컴파일러 만족용
            case ISSUED -> new IllegalStateException("unreachable");
        };
    }
}
