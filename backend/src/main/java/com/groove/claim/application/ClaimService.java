package com.groove.claim.application;

import com.groove.claim.domain.Claim;
import com.groove.claim.domain.ClaimItem;
import com.groove.claim.domain.ClaimRepository;
import com.groove.claim.domain.ClaimStatus;
import com.groove.claim.exception.ClaimItemNotInOrderException;
import com.groove.claim.exception.ClaimNotFoundException;
import com.groove.claim.exception.EmptyClaimException;
import com.groove.claim.exception.ExcessiveReturnQuantityException;
import com.groove.claim.exception.OrderNotReturnableException;
import com.groove.claim.exception.ReturnWindowExpiredException;
import com.groove.claim.exception.ReturnWindowNotDeterminableException;
import com.groove.coupon.application.CouponApplicationService;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentNotFoundException;
import com.groove.payment.exception.PaymentNotRefundableException;
import com.groove.payment.gateway.GatewayRefunds;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.RefundRequest;
import com.groove.shipping.application.ShippingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 반품(claim) 접수/승인/거부/환불 트랜잭션 경계 (#239).
 *
 * <p>주문·결제·쿠폰·재고·배송 여러 도메인을 조율하므로 별도 claim 모듈의 ApplicationService 에 둔다 — 발송 전 즉시
 * 취소({@code AdminOrderService.refund}, 배송완료 전)와 의미·경로가 분리된 배송완료 후 역물류 유스케이스다.
 *
 * <p>접수(request) 검증 순서: 주문 존재(404) → 본인 주문(타인/게스트 404, 존재 노출 방지) → 반품 자격
 * {DELIVERED, COMPLETED}(422) → 반품 기한(배송완료 + window 초과 422, 배송완료 시각 부재 422) → 항목 1개 이상(422)
 * → 항목 소속·잔여 수량 가드(주문 없음 422 / 초과 409).
 *
 * <p>환불(completeRefund)은 {@code AdminOrderService.refund} 와 같은 단일 트랜잭션 보상 패턴을 부분 환불로 확장한다:
 * Payment FOR UPDATE 락 → 멱등(INSPECTING 아니면 no-op) → PG refund(claim 별 멱등 키) → Payment 부분/전액 환불 누적
 * → 검수 통과 항목 재입고 → 전량 반품 완성 시 쿠폰 복원 + 주문 반품 마커. 어느 단계든 실패하면 전체 롤백되고, claim 별
 * 결정적 멱등 키로 재시도 시 PG 실호출 1회를 보장한다. 발송완료 주문은 CANCELLED 로 되돌리지 않아 "배송된 사실"을
 * 유지한다(OrderStatus 상태 폭발 회피).
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ClaimRepository claimRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final CouponApplicationService couponApplicationService;
    private final ShippingService shippingService;
    private final Clock clock;
    private final Duration returnWindow;

    public ClaimService(ClaimRepository claimRepository,
                        OrderRepository orderRepository,
                        PaymentRepository paymentRepository,
                        PaymentGateway paymentGateway,
                        CouponApplicationService couponApplicationService,
                        ShippingService shippingService,
                        Clock clock,
                        @Value("${groove.claim.return-window:P7D}") Duration returnWindow) {
        this.claimRepository = claimRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.couponApplicationService = couponApplicationService;
        this.shippingService = shippingService;
        this.clock = clock;
        this.returnWindow = returnWindow;
    }

    @Transactional
    public Claim request(ClaimCreateCommand command) {
        // 동시 중복 접수 직렬화 — 같은 주문에 두 요청이 잔여 수량 가드를 동시에 통과해 과다 반품되는 것을 막는다(#239).
        // 주문 행을 PESSIMISTIC_WRITE 로 잠가, 뒤따르는 잔여 수량 집계가 직전 커밋된 다른 접수를 반영하도록 직렬화한다.
        Order order = orderRepository.findByOrderNumberForUpdate(command.orderNumber())
                .orElseThrow(OrderNotFoundException::new);
        // 본인 주문만 — memberId 가 null(게스트)이거나 불일치면 존재를 노출하지 않으려 404 로 통일한다.
        if (command.memberId() == null || !Objects.equals(order.getMemberId(), command.memberId())) {
            throw new OrderNotFoundException();
        }
        if (!OrderStatus.DELIVERED_OR_COMPLETED.contains(order.getStatus())) {
            throw new OrderNotReturnableException(order.getStatus());
        }
        // 반품 기한 anchor = 배송완료 시각. 배송행이 없거나(관리자 강제 DELIVERED 등) deliveredAt 미기록이면 주문의
        // 마지막 변경 시각(updated_at)으로 폴백한다 — 그조차 없으면(영속 전) 기한 산정 불가로 거부한다.
        Instant deliveredAt = shippingService.findDeliveredAt(order.getId())
                .or(() -> Optional.ofNullable(order.getUpdatedAt()))
                .orElseThrow(ReturnWindowNotDeterminableException::new);
        if (clock.instant().isAfter(deliveredAt.plus(returnWindow))) {
            throw new ReturnWindowExpiredException();
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new EmptyClaimException();
        }

        Map<Long, OrderItem> orderItems = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            orderItems.put(item.getId(), item);
        }
        // 같은 orderItemId 가 여러 줄로 와도 합산해 한 번에 검증한다.
        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (ClaimCreateCommand.Line line : command.lines()) {
            requested.merge(line.orderItemId(), line.quantity(), Integer::sum);
        }
        Map<Long, Integer> alreadyReturned = returnedQuantitiesByOrderItem(order.getId());

        Claim claim = Claim.request(order, command.reason());
        for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
            Long orderItemId = entry.getKey();
            int quantity = entry.getValue();
            OrderItem orderItem = orderItems.get(orderItemId);
            if (orderItem == null) {
                throw new ClaimItemNotInOrderException(orderItemId);
            }
            int returnable = orderItem.getQuantity() - alreadyReturned.getOrDefault(orderItemId, 0);
            if (quantity > returnable) {
                throw new ExcessiveReturnQuantityException(orderItemId, quantity, returnable);
            }
            claim.addItem(ClaimItem.of(orderItem, quantity));
        }
        Claim saved = claimRepository.save(claim);
        log.info("반품 접수 — claimId={}, order={}, 항목 {}건", saved.getId(), order.getOrderNumber(), requested.size());
        return saved;
    }

    /** 관리자 승인 — REQUESTED → APPROVED. 이후 회수·검수는 스케줄러가 자동 진행한다. */
    @Transactional
    public Claim approve(Long claimId) {
        Claim claim = claimRepository.findByIdForUpdate(claimId).orElseThrow(ClaimNotFoundException::new);
        claim.approve(clock.instant());
        log.info("반품 승인 — claimId={}", claimId);
        return claim;
    }

    /** 관리자 거부 — REQUESTED(접수 반려) 또는 INSPECTING(검수 불합격) → REJECTED. 재입고·환불 없음. */
    @Transactional
    public Claim reject(Long claimId, String reason) {
        // 반품 행을 잠가 동시 completeRefund 와 직렬화한다 — 검수통과 환불이 진행 중인 반품을 거부로 덮어쓰거나,
        // 이미 환불된 반품을 거부로 되돌리는 race 를 막는다(#239). REFUNDED 종착 후 거부 시도는 409 로 거부된다.
        Claim claim = claimRepository.findByIdForUpdate(claimId).orElseThrow(ClaimNotFoundException::new);
        claim.reject(reason, clock.instant());
        log.info("반품 거부 — claimId={}, status(before)={}", claimId, claim.getStatus());
        return claim;
    }

    /** 스케줄러 위임 — APPROVED 인 반품을 IN_TRANSIT(회수 시작)으로. 상태 불일치면 무해 무시(재전달 방어). */
    @Transactional
    public void advanceToInTransit(Long claimId) {
        claimRepository.findByIdForUpdate(claimId).ifPresent(claim -> {
            if (claim.getStatus() == ClaimStatus.APPROVED) {
                claim.startTransit(clock.instant());
            }
        });
    }

    /** 스케줄러 위임 — IN_TRANSIT 인 반품을 INSPECTING(검수 시작)으로. 상태 불일치면 무해 무시. */
    @Transactional
    public void advanceToInspecting(Long claimId) {
        claimRepository.findByIdForUpdate(claimId).ifPresent(claim -> {
            if (claim.getStatus() == ClaimStatus.IN_TRANSIT) {
                claim.startInspecting(clock.instant());
            }
        });
    }

    /**
     * 검수 통과 환불 (스케줄러 자동통과 또는 관리자 수동 complete) — INSPECTING → REFUNDED.
     *
     * <p>INSPECTING 이 아니면 부수효과 없이 현재 상태를 반환한다(멱등 — 스케줄러 재전달·관리자 중복 클릭 방어). 부분
     * 반품이면 결제는 PARTIALLY_REFUNDED, 누적 환불액이 결제 전액에 도달하면 REFUNDED 가 되고 그때만 쿠폰 복원 +
     * 주문 반품 마커를 찍는다.
     */
    @Transactional
    public Claim completeRefund(Long claimId) {
        // 반품 행을 PESSIMISTIC_WRITE 로 잠가 동시 completeRefund(스케줄러 + 관리자 수동 complete) 및 reject 와
        // 직렬화한다 — 락 이후 읽는 상태는 최신 커밋본이라, 이미 환불/거부된 반품에 재진입하면 멱등 no-op 으로 빠진다.
        // 이게 없으면 거부된 반품이 환불로 되살아나거나(stale 스냅샷), 같은 반품이 이중 정산되는 race 가 생긴다(#239).
        Claim claim = claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(ClaimNotFoundException::new);
        if (claim.getStatus() != ClaimStatus.INSPECTING) {
            return claim;
        }
        Order order = claim.getOrder();
        Long orderId = order.getId();
        // Payment 를 PESSIMISTIC_WRITE 로 잠가 동시 환불(발송 전 즉시취소 경로 포함)을 직렬화한다. DELIVERED 이후 주문은
        // canTransitionTo(CANCELLED) 가 false 라 발송 전 경로와는 실제로 겹치지 않는다 — 다중 부분 반품 간 직렬화가 주 목적.
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(PaymentNotFoundException::new);
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new PaymentNotRefundableException(payment.getStatus());
        }

        long refundAmount = computeRefundAmount(order, claim, payment);
        Instant now = clock.instant();
        // 환불액 0(환불 가능액 소진 — 정상 경로에선 결제 상태 가드가 차단하는 방어선)이면 PG 호출/누적 갱신을 건너뛴다.
        // RefundRequest 는 양수만 허용하므로 0 을 넘기면 예외가 되기 때문이다. 재입고/상태 전이는 그대로 진행한다.
        if (refundAmount > 0) {
            callGatewayRefund(payment, claim, refundAmount);
            payment.refund(refundAmount, now);
        }
        // 검수 통과 항목 재입고 — claim 락 보유 트랜잭션 안에서 claimItem.orderItem.album 을 지연 로드한다(반품 항목
        // 수가 적어 N+1 비용 미미). 불합격(REJECTED)은 이 경로를 타지 않으므로 미재입고.
        for (ClaimItem item : claim.getItems()) {
            item.getOrderItem().getAlbum().adjustStock(item.getQuantity());
        }
        boolean fullyReturned = payment.getStatus() == PaymentStatus.REFUNDED;
        if (fullyReturned) {
            // 전량 반품 — 쿠폰 USED→ISSUED 복원(부분 반품은 부당이득 방지로 미복원) + 주문 전량 반품 마커.
            couponApplicationService.restoreForOrder(orderId);
            order.markReturned(now);
        }
        claim.markRefunded(refundAmount, now);
        log.info("반품 환불 완료 — claimId={}, order={}, 환불액={}, 결제상태={}, 전량반품={}",
                claimId, order.getOrderNumber(), refundAmount, payment.getStatus(), fullyReturned);
        return claim;
    }

    @Transactional(readOnly = true)
    public Claim findForMember(Long memberId, Long claimId) {
        return claimRepository.findByIdAndOrder_MemberId(claimId, memberId)
                .orElseThrow(ClaimNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Claim findDetail(Long claimId) {
        return claimRepository.findWithItemsAndOrderById(claimId)
                .orElseThrow(ClaimNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<Claim> list(ClaimStatus status, Pageable pageable) {
        return status == null
                ? claimRepository.findAll(pageable)
                : claimRepository.findByStatus(status, pageable);
    }

    /**
     * claim 환불액 = 비례 배분 누적 목표 − 기환불액. 누적 반품 정가(이미 REFUNDED + 이번 claim)가 총정가에서
     * 차지하는 비율만큼의 실결제액(payable)을 목표로 잡고, 기환불액을 뺀 증분을 이번 환불액으로 한다.
     *
     * <p>이 누적 방식은 전량 반품(누적 정가 == 총정가)일 때 목표가 정확히 payable 이 되어 증분 = 잔여(payable −
     * 기환불)로 수렴하므로 반올림 드리프트가 마지막 claim 에서 자동 정산된다. target ≤ payable 이라 증분은 항상 잔여
     * 한도 이내다.
     */
    private long computeRefundAmount(Order order, Claim claim, Payment payment) {
        long alreadyRefundedGross = claimRepository.findByOrder_IdAndStatus(order.getId(), ClaimStatus.REFUNDED)
                .stream().mapToLong(Claim::getGross).sum();
        return proportionalRefund(order.getPayableAmount(), order.getTotalAmount(),
                alreadyRefundedGross + claim.getGross(), payment.getRefundedAmount());
    }

    /**
     * 비례 배분 환불 증분 = 이번 누적 목표 − 기환불액 (#239).
     *
     * <p>누적 반품 정가({@code cumGrossIncl})가 총정가({@code totalGross})에서 차지하는 비율만큼의 실결제액
     * ({@code payable})을 목표로 잡고, 기환불액을 뺀 증분을 이번 환불액으로 한다. 전량 반품
     * ({@code cumGrossIncl == totalGross})이면 목표가 정확히 {@code payable} 이라 증분이 잔여로 수렴해 반올림
     * 드리프트가 마지막 claim 에서 자동 정산된다. {@code payable × cumGrossIncl} 오버플로를 막으려 BigInteger 로
     * 곱셈/반올림한다. 반올림으로 증분이 0 이하가 되는 희귀 케이스는 1원(잔여 한도 내)으로 전진시켜
     * {@code RefundRequest} 의 양수 제약을 만족시킨다.
     */
    static long proportionalRefund(long payable, long totalGross, long cumGrossIncl, long alreadyRefunded) {
        long remaining = payable - alreadyRefunded;
        if (remaining <= 0) {
            // 환불 가능액 소진 — 0 을 돌려주고 호출 측이 PG/누적 갱신을 건너뛴다(정상 경로에선 결제 상태 가드가 먼저 차단).
            return 0;
        }
        long target = totalGross == 0 ? payable
                : BigInteger.valueOf(payable).multiply(BigInteger.valueOf(cumGrossIncl))
                        .add(BigInteger.valueOf(totalGross / 2))
                        .divide(BigInteger.valueOf(totalGross))
                        .longValueExact();
        long increment = target - alreadyRefunded;
        // 증분이 양수면 잔여 한도로 캡(반올림 초과 방지), 0 이하면 최소 1원으로 전진(remaining ≥ 1 보장).
        return increment > 0 ? Math.min(increment, remaining) : 1;
    }

    private void callGatewayRefund(Payment payment, Claim claim, long amount) {
        // claim 별 결정적 멱등 키 — 보상 트랜잭션 부분 실패 후 재시도에도 해당 claim 의 PG 실호출 1회 보장 (#72).
        RefundRequest request = new RefundRequest(
                payment.getPgTransactionId(), amount, claim.getReason(), payment.refundIdempotencyKey(claim.getId()));
        GatewayRefunds.refund(paymentGateway, request);
    }

    private Map<Long, Integer> returnedQuantitiesByOrderItem(Long orderId) {
        Map<Long, Integer> map = new HashMap<>();
        // 거부(REJECTED)를 제외한 모든 반품(활성 + 완료)의 항목 수량을 합산 — 상품이 실제 반품된 것만 잔여에서 차감.
        for (Claim claim : claimRepository.findByOrder_IdAndStatusNot(orderId, ClaimStatus.REJECTED)) {
            for (ClaimItem item : claim.getItems()) {
                map.merge(item.getOrderItem().getId(), item.getQuantity(), Integer::sum);
            }
        }
        return map;
    }
}
