package com.groove.claim.application;

import com.groove.claim.domain.Claim;
import com.groove.claim.domain.ClaimItem;
import com.groove.claim.domain.ClaimRepository;
import com.groove.claim.domain.ClaimStatus;
import com.groove.claim.exception.ClaimItemNotInOrderException;
import com.groove.claim.exception.ClaimNotFoundException;
import com.groove.claim.exception.EmptyClaimException;
import com.groove.claim.exception.ExcessiveReturnQuantityException;
import com.groove.claim.exception.OrderNotCancellableException;
import com.groove.claim.exception.OrderNotReturnableException;
import com.groove.claim.exception.ReturnWindowExpiredException;
import com.groove.claim.exception.ReturnWindowNotDeterminableException;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.StockRestorer;
import com.groove.coupon.application.CouponApplicationService;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 반품(claim) 접수/승인/거부/환불 트랜잭션 경계.
 *
 * <p>접수(request) 검증 순서: 주문 존재(404) → 본인 주문(404) → 회원 활성(404, 탈퇴 토큰 잔존 방어 #269) → 반품 자격 {DELIVERED, COMPLETED}(422) → 반품
 * 기한(422) → 항목 1개 이상(422) → 항목 소속·잔여 수량 가드(422 / 409).
 *
 * <p>환불(completeRefund): Payment FOR UPDATE 락 → 멱등(INSPECTING 아니면 no-op) → PG refund(claim 별 멱등 키)
 * → Payment 부분/전액 환불 누적 → 검수 통과 항목 재입고 → 전량 반품 완성 시 쿠폰 복원 + 주문 반품 마커.
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    /** 부분 취소(CANCEL) 자격 주문 상태 — PAID/PREPARING. */
    private static final Set<OrderStatus> CANCELLABLE_ORDER_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PREPARING);

    private final ClaimRepository claimRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final CouponApplicationService couponApplicationService;
    private final ShippingService shippingService;
    private final AlbumRepository albumRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;
    private final Duration returnWindow;

    public ClaimService(ClaimRepository claimRepository,
                        OrderRepository orderRepository,
                        PaymentRepository paymentRepository,
                        PaymentGateway paymentGateway,
                        CouponApplicationService couponApplicationService,
                        ShippingService shippingService,
                        AlbumRepository albumRepository,
                        MemberRepository memberRepository,
                        Clock clock,
                        @Value("${groove.claim.return-window:P7D}") Duration returnWindow) {
        this.claimRepository = claimRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.couponApplicationService = couponApplicationService;
        this.shippingService = shippingService;
        this.albumRepository = albumRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
        this.returnWindow = returnWindow;
    }

    @Transactional
    public Claim request(ClaimCreateCommand command) {
        // 주문 행 PESSIMISTIC_WRITE 로 동시 접수 직렬화.
        Order order = orderRepository.findByOrderNumberForUpdate(command.orderNumber())
                .orElseThrow(OrderNotFoundException::new);
        // 본인 주문만 — memberId null 이거나 불일치면 404.
        if (command.memberId() == null || !Objects.equals(order.getMemberId(), command.memberId())) {
            throw new OrderNotFoundException();
        }
        // 탈퇴(soft delete) 회원이 만료 전 access 토큰으로 반품 접수하는 것을 차단(#269).
        if (!memberRepository.existsByIdAndDeletedAtIsNull(command.memberId())) {
            throw new MemberNotFoundException();
        }
        if (!OrderStatus.DELIVERED_OR_COMPLETED.contains(order.getStatus())) {
            throw new OrderNotReturnableException(order.getStatus());
        }
        // 반품 기한 anchor = 결정적 배송완료 시각. order.deliveredAt(DELIVERED 전이 시 기록)을 1차 기준으로
        // 삼는다 — 정상 파이프라인·관리자 강제 전이·시드가 모두 Order.changeStatus 를 거치므로 배송 행 유무와
        // 무관하게 채워진다. delivered_at 컬럼(V33) 도입 전 배송완료된 레거시 주문만 shipping.deliveredAt 로
        // 보강하고, 그조차 없으면(배송 행 없는 과거 강제 전이) 결정 불가로 거부한다. updatedAt 폴백(비결정적)은
        // 쓰지 않는다.
        Instant deliveredAt = Optional.ofNullable(order.getDeliveredAt())
                .or(() -> shippingService.findDeliveredAt(order.getId()))
                .orElseThrow(ReturnWindowNotDeterminableException::new);
        if (clock.instant().isAfter(deliveredAt.plus(returnWindow))) {
            throw new ReturnWindowExpiredException();
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new EmptyClaimException();
        }

        Map<Long, Integer> requested = mergeRequestedQuantities(command.lines());
        Claim claim = Claim.request(order, command.reason());
        addValidatedItems(claim, orderItemsById(order), requested, claimedQuantitiesByOrderItem(order.getId()));
        Claim saved = claimRepository.save(claim);
        log.info("반품 접수 — claimId={}, order={}, 항목 {}건", saved.getId(), order.getOrderNumber(), requested.size());
        return saved;
    }

    /**
     * 관리자 발송 전 부분 취소 — CANCEL 클레임을 즉시 환불 확정한다(회수·검수 없음).
     *
     * <p>흐름: 주문 FOR UPDATE → 자격(PAID/PREPARING) → 항목 소속·취소가능 수량 가드 → 결제 FOR
     * UPDATE(PAID/PARTIALLY_REFUNDED) → CANCEL 클레임 저장 → 쿠폰 인지 환불액 산출 → (양수면) PG 환불 + 결제
     * 부분/전액 누적 → 취소 수량 재입고 → 쿠폰 무효 시 복원 → 전량 취소면 주문 CANCELLED + 발송 전 배송 취소 →
     * 클레임 REFUNDED 확정.
     *
     * <p>쿠폰 정책: 부분취소 후 잔여 정가 ≥ 최소주문금액이면 할인 안분(쿠폰 USED 유지), 미만이면 쿠폰을 무효화해 적용
     * 할인분을 환불에서 제외하고 쿠폰을 복원한다. 적용 할인이 취소 품목 정가를 초과하면 무효화하지 않고 안분으로 폴백한다.
     */
    @Transactional
    public Claim cancelPartially(OrderPartialCancelCommand command) {
        // 주문 행 PESSIMISTIC_WRITE 로 동시 부분취소 직렬화.
        Order order = orderRepository.findByOrderNumberForUpdate(command.orderNumber())
                .orElseThrow(OrderNotFoundException::new);
        if (!CANCELLABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new OrderNotCancellableException(order.getStatus());
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new EmptyClaimException();
        }

        // 항목 소속·취소가능 수량 검증을 결제 락 전에 먼저.
        Map<Long, Integer> requested = mergeRequestedQuantities(command.lines());
        Map<Long, Integer> alreadyClaimed = claimedQuantitiesByOrderItem(order.getId());
        Claim claim = Claim.requestCancellation(order, command.reason());
        addValidatedItems(claim, orderItemsById(order), requested, alreadyClaimed);

        // 결제 FOR UPDATE 로 다중 환불 직렬화.
        Payment payment = paymentRepository.findByOrderIdForUpdate(order.getId())
                .orElseThrow(PaymentNotFoundException::new);
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new PaymentNotRefundableException(payment.getStatus());
        }
        Claim saved = claimRepository.save(claim);

        // 전량 취소 = 모든 OrderItem 의 (기클레임 + 이번 요청) 수량이 주문 수량에 도달.
        boolean fullyCancelled = order.getItems().stream().allMatch(item ->
                alreadyClaimed.getOrDefault(item.getId(), 0) + requested.getOrDefault(item.getId(), 0)
                        >= item.getQuantity());

        // 쿠폰 인지 환불액 (allowCouponVoid=true).
        OptionalLong couponMinOrder = couponApplicationService.appliedCouponMinOrderAmount(order.getId());
        long cumCancelledGross = couponMinOrder.isPresent()
                ? refundedGrossSoFar(order.getId()) + saved.getGross() : saved.getGross();
        RefundComputation refund = refundIncrement(order.getPayableAmount(), order.getTotalAmount(),
                saved.getGross(), cumCancelledGross, payment.getRefundedAmount(), couponMinOrder, true);

        Instant now = clock.instant();
        settleClaimRefund(payment, saved, refund.amount(), now);
        restoreClaimStock(saved);
        // 쿠폰 복원: 무효(잔여<최소주문금액) 또는 전량 취소.
        if (refund.voidCoupon() || fullyCancelled) {
            couponApplicationService.restoreForOrder(order.getId());
        }
        if (fullyCancelled) {
            // 전량 취소 — 주문 CANCELLED + 발송 전 배송 취소.
            order.changeStatus(OrderStatus.CANCELLED, command.reason(), now);
            shippingService.cancelForOrder(order.getId());
        }
        saved.markCancelRefunded(refund.amount(), now);
        log.info("부분 취소 환불 — claimId={}, order={}, 환불액={}, 쿠폰무효={}, 전량취소={}, 결제상태={}",
                saved.getId(), order.getOrderNumber(), refund.amount(), refund.voidCoupon(), fullyCancelled,
                payment.getStatus());
        return saved;
    }

    /** 관리자 승인 — REQUESTED → APPROVED. */
    @Transactional
    public Claim approve(Long claimId) {
        Claim claim = claimRepository.findByIdForUpdate(claimId).orElseThrow(ClaimNotFoundException::new);
        claim.approve(clock.instant());
        log.info("반품 승인 — claimId={}", claimId);
        return claim;
    }

    /** 관리자 거부 — REQUESTED 또는 INSPECTING → REJECTED. 재입고·환불 없음. */
    @Transactional
    public Claim reject(Long claimId, String reason) {
        // 반품 행 FOR UPDATE 로 동시 completeRefund 와 직렬화.
        Claim claim = claimRepository.findByIdForUpdate(claimId).orElseThrow(ClaimNotFoundException::new);
        claim.reject(reason, clock.instant());
        log.info("반품 거부 — claimId={}, status(before)={}", claimId, claim.getStatus());
        return claim;
    }

    /** 스케줄러 위임 — APPROVED 인 반품을 IN_TRANSIT 으로. 상태 불일치면 무시. */
    @Transactional
    public void advanceToInTransit(Long claimId) {
        claimRepository.findByIdForUpdate(claimId).ifPresent(claim -> {
            if (claim.getStatus() == ClaimStatus.APPROVED) {
                claim.startTransit(clock.instant());
            }
        });
    }

    /** 스케줄러 위임 — IN_TRANSIT 인 반품을 INSPECTING 으로. 상태 불일치면 무시. */
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
     * <p>INSPECTING 이 아니면 부수효과 없이 현재 상태를 반환한다(멱등). 부분 반품이면 결제는 PARTIALLY_REFUNDED,
     * 누적 환불액이 결제 전액에 도달하면 REFUNDED 가 되고 그때만 쿠폰 복원 + 주문 반품 마커를 찍는다.
     */
    @Transactional
    public Claim completeRefund(Long claimId) {
        // 반품 행 FOR UPDATE 로 동시 completeRefund 및 reject 와 직렬화.
        Claim claim = claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(ClaimNotFoundException::new);
        if (claim.getStatus() != ClaimStatus.INSPECTING) {
            return claim;
        }
        Order order = claim.getOrder();
        Long orderId = order.getId();
        // Payment FOR UPDATE 로 다중 부분 반품 환불 직렬화.
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(PaymentNotFoundException::new);
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new PaymentNotRefundableException(payment.getStatus());
        }

        // 환불액 (allowCouponVoid=false). 쿠폰 적용 중이면 비례 배분, 미적용이면 반품 품목 정가만 환불.
        OptionalLong couponMinOrder = couponApplicationService.appliedCouponMinOrderAmount(orderId);
        long cumReturnedGross = couponMinOrder.isPresent()
                ? refundedGrossSoFar(orderId) + claim.getGross() : claim.getGross();
        long refundAmount = refundIncrement(order.getPayableAmount(), order.getTotalAmount(),
                claim.getGross(), cumReturnedGross, payment.getRefundedAmount(), couponMinOrder, false).amount();
        Instant now = clock.instant();
        settleClaimRefund(payment, claim, refundAmount, now);
        // 검수 통과 항목 재입고.
        restoreClaimStock(claim);
        boolean fullyReturned = payment.getStatus() == PaymentStatus.REFUNDED;
        if (fullyReturned) {
            // 전량 반품 — 쿠폰 복원 + 주문 전량 반품 마커.
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

    /** 한 주문에서 이미 REFUNDED 된 클레임(취소 + 반품)의 정가 합. */
    private long refundedGrossSoFar(Long orderId) {
        return claimRepository.findByOrder_IdAndStatus(orderId, ClaimStatus.REFUNDED)
                .stream().mapToLong(Claim::getGross).sum();
    }

    /**
     * 비례 배분 환불 증분 = 이번 누적 목표 − 기환불액.
     *
     * <p>누적 반품 정가(cumGrossIncl)가 총정가(totalGross)에서 차지하는 비율만큼의 실결제액(payable)을 목표로 잡고,
     * 기환불액을 뺀 증분을 이번 환불액으로 한다. BigInteger 로 곱셈/반올림한다.
     */
    static long proportionalRefund(long payable, long totalGross, long cumGrossIncl, long alreadyRefunded) {
        long remaining = payable - alreadyRefunded;
        if (remaining <= 0) {
            // 환불 가능액 소진 — 0 반환.
            return 0;
        }
        long target = totalGross == 0 ? payable
                : BigInteger.valueOf(payable).multiply(BigInteger.valueOf(cumGrossIncl))
                        .add(BigInteger.valueOf(totalGross / 2))
                        .divide(BigInteger.valueOf(totalGross))
                        .longValueExact();
        long increment = target - alreadyRefunded;
        // 양수면 잔여 한도로 캡, 0 이하면 최소 1원으로 전진.
        return increment > 0 ? Math.min(increment, remaining) : 1;
    }

    /**
     * 환불 증분 + 쿠폰 무효 여부 — 취소(allowCouponVoid=true)·반품(false) 공용 환불 계산. 세 분기:
     *
     * <ul>
     *   <li>쿠폰 미적용(couponMinOrder.isEmpty()) → 이번 클레임 자기 정가(thisClaimGross)만 환불(잔여 한도 캡).</li>
     *   <li>취소 무효(allowCouponVoid 이고 부분취소 후 잔여 정가 &lt; 최소주문금액) → 누적 환불 목표 =
     *       cumGrossIncl − discount, 호출 측이 쿠폰 복원. 증분 0 이하면 비례로 폴백.</li>
     *   <li>그 외 → proportionalRefund 비례 배분.</li>
     * </ul>
     */
    static RefundComputation refundIncrement(long payable, long totalGross, long thisClaimGross,
                                             long cumGrossIncl, long alreadyRefunded,
                                             OptionalLong couponMinOrder, boolean allowCouponVoid) {
        long remaining = payable - alreadyRefunded;
        if (remaining <= 0) {
            return new RefundComputation(0, false);
        }
        if (couponMinOrder.isEmpty()) {
            return new RefundComputation(Math.min(thisClaimGross, remaining), false);
        }
        if (allowCouponVoid && totalGross - cumGrossIncl < couponMinOrder.getAsLong()) {
            long discount = totalGross - payable;
            long increment = (cumGrossIncl - discount) - alreadyRefunded;
            if (increment > 0) {
                return new RefundComputation(Math.min(increment, remaining), true);
            }
            // 증분 0 이하 — 비례 폴백.
        }
        return new RefundComputation(proportionalRefund(payable, totalGross, cumGrossIncl, alreadyRefunded), false);
    }

    /** 환불 산출 결과 — 이번 환불 증분과 쿠폰 무효(복원) 여부. */
    record RefundComputation(long amount, boolean voidCoupon) {
    }

    private void callGatewayRefund(Payment payment, Claim claim, long amount) {
        // claim 별 결정적 멱등 키.
        RefundRequest request = new RefundRequest(
                payment.getPgTransactionId(), amount, claim.getReason(), payment.refundIdempotencyKey(claim.getId()));
        GatewayRefunds.refund(paymentGateway, request);
    }

    /** PG 환불 호출 + 결제 부분/전액 누적 — 환불액이 양수일 때만 (취소/반품 공용). */
    private void settleClaimRefund(Payment payment, Claim claim, long amount, Instant now) {
        if (amount > 0) {
            callGatewayRefund(payment, claim, amount);
            payment.refund(amount, now);
        }
    }

    /** 클레임 항목 수량을 재입고 (취소/반품 공용) — 원자적 가산 UPDATE(StockRestorer, albumId 오름차순). */
    private void restoreClaimStock(Claim claim) {
        StockRestorer.restore(albumRepository, claim.getItems().stream()
                .collect(Collectors.groupingBy(item -> item.getOrderItem().getAlbum().getId(),
                        Collectors.summingInt(ClaimItem::getQuantity))));
    }

    /** order_item_id → OrderItem 인덱스. */
    private static Map<Long, OrderItem> orderItemsById(Order order) {
        Map<Long, OrderItem> map = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            map.put(item.getId(), item);
        }
        return map;
    }

    /** 요청 라인을 orderItemId 별 수량으로 합산. */
    private static Map<Long, Integer> mergeRequestedQuantities(List<ClaimCreateCommand.Line> lines) {
        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (ClaimCreateCommand.Line line : lines) {
            requested.merge(line.orderItemId(), line.quantity(), Integer::sum);
        }
        return requested;
    }

    /**
     * 요청 항목을 검증해 claim 에 추가한다 (취소/반품 접수 공용) — 주문 소속(없으면 422)과 잔여 초과(409)를 가드한 뒤
     * ClaimItem 으로 붙인다.
     */
    private void addValidatedItems(Claim claim, Map<Long, OrderItem> orderItems,
                                   Map<Long, Integer> requested, Map<Long, Integer> alreadyClaimed) {
        for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
            Long orderItemId = entry.getKey();
            int quantity = entry.getValue();
            OrderItem orderItem = orderItems.get(orderItemId);
            if (orderItem == null) {
                throw new ClaimItemNotInOrderException(orderItemId);
            }
            int claimable = orderItem.getQuantity() - alreadyClaimed.getOrDefault(orderItemId, 0);
            if (quantity > claimable) {
                throw new ExcessiveReturnQuantityException(orderItemId, quantity, claimable);
            }
            claim.addItem(ClaimItem.of(orderItem, quantity));
        }
    }

    /**
     * 한 주문에서 OrderItem 별 이미 클레임된 수량 — 거부(REJECTED)를 제외한 모든 클레임의 항목 수량 합.
     */
    private Map<Long, Integer> claimedQuantitiesByOrderItem(Long orderId) {
        Map<Long, Integer> map = new HashMap<>();
        for (Claim claim : claimRepository.findByOrder_IdAndStatusNot(orderId, ClaimStatus.REJECTED)) {
            for (ClaimItem item : claim.getItems()) {
                map.merge(item.getOrderItem().getId(), item.getQuantity(), Integer::sum);
            }
        }
        return map;
    }
}
