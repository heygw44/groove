package com.groove.coupon.application;

import com.groove.coupon.domain.Coupon;
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
import java.time.Instant;

/**
 * 회원 쿠폰을 주문에 적용/복원하는 조율 서비스 (#91, docs/plans/coupon-system.md §5).
 *
 * <p>두 호출자를 가진다:
 * <ul>
 *   <li>{@code OrderService.place} — 주문 생성 시 적용 ({@link #applyToOrder}).</li>
 *   <li>{@code OrderService.cancel} + {@code AdminOrderService.refund} — 취소·환불 시 복원
 *       ({@link #restoreForOrder}). 양 경로 모두 호출되어야 한다 (이슈 #91 DoD HIGH 리스크).</li>
 * </ul>
 *
 * <h2>트랜잭션 경계</h2>
 * <p>두 진입점 모두 {@code Propagation.MANDATORY} — 호출자({@code OrderService.place/cancel},
 * {@code AdminOrderService.refund}) 의 {@code @Transactional} 안에서 실행된다. 적용은 주문 저장과
 * 같은 트랜잭션이어야 {@code MemberCoupon.use(orderId)} 와 {@code Order.discountAmount} 가 함께
 * 커밋된다 — 한쪽만 커밋되면 정합성이 깨진다.
 *
 * <h2>동시성</h2>
 * <p>{@link #applyToOrder} 는 {@code findByIdForUpdate} 로 회원 쿠폰 행을 잠근다 — 같은 쿠폰을
 * 동시에 두 주문에 적용하려는 race 에서 두 번째는 첫 번째 커밋 후 {@code status=USED} 를 본다.
 * {@link #restoreForOrder} 는 단일 row 변경이라 추가 락이 필요 없다 — 주문 상태 머신이 cancel 과
 * refund 의 동시 진입을 막는다 (cancel = PENDING 한정, refund = PAID 한정).
 */
@Service
public class CouponApplicationService {

    private final MemberCouponRepository memberCouponRepository;
    private final Clock clock;

    public CouponApplicationService(MemberCouponRepository memberCouponRepository, Clock clock) {
        this.memberCouponRepository = memberCouponRepository;
        this.clock = clock;
    }

    /**
     * 회원 쿠폰을 주문에 적용한다 — 검증 → 할인 계산 → {@code Order.applyDiscount} → {@code use(orderId)}.
     *
     * <p>호출 시점: {@code orderRepository.save(order)} 직후 (orderId 가 부여된 뒤). 같은 트랜잭션이므로
     * {@code MemberCoupon.use} 의 dirty 변경은 커밋 시 함께 영속화된다. 검증 실패는 예외로 트랜잭션 전체를
     * 롤백시켜 재고 차감·주문 저장도 되돌아가게 한다.
     *
     * <p>검증 순서 (저비용 → 고비용, fail-fast):
     * <ol>
     *   <li>존재 → {@link CouponNotFoundException} (404)</li>
     *   <li>소유자 → {@link CouponNotOwnedException} (403, 메시지에 memberId 노출 안 함)</li>
     *   <li>상태 ISSUED 가 아님 → USED/EXPIRED/CANCELLED 별 매핑</li>
     *   <li>만료 시각 경과 → {@link CouponExpiredException} (422)</li>
     *   <li>{@link Coupon#calculateDiscount} (min_order, 정액/정률, 상한, {@code discount ≤ subtotal})</li>
     *   <li>{@code order.applyDiscount} (PENDING + 0 ≤ discount ≤ total 가드)</li>
     *   <li>{@code memberCoupon.use(orderId)} (ISSUED → USED 전이)</li>
     * </ol>
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
     * 주문에 적용된 쿠폰을 복원한다 — USED → ISSUED. 쿠폰 미적용 주문은 no-op.
     *
     * <p>호출 시점: {@code OrderService.cancel} 의 재고 복원 직후, {@code AdminOrderService.refund} 의
     * 재고 복원 직후. {@code MemberCoupon.restore} 가 {@code usedAt} 과 {@code orderId} 를 함께 비운다.
     *
     * <p>방어적 가드: 사용된 쿠폰의 상태가 어떤 이유로 ISSUED 인 채라면(예: 외부 수정) 복원하지 않고 no-op —
     * {@code MemberCoupon.restore()} 가 던질 {@link IllegalStateException} 을 피하기 위해 사전에 가른다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreForOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        memberCouponRepository.findByOrderId(orderId).ifPresent(memberCoupon -> {
            if (memberCoupon.getStatus() == MemberCouponStatus.USED) {
                memberCoupon.restore();
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
