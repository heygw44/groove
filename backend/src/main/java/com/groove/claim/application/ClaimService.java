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
import com.groove.catalog.album.event.AlbumStockChangedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
 * 접수 검증 순서가 비자명: 주문 존재 → 본인 주문 → 회원 활성(탈퇴 토큰 방어) → 반품 자격 → 반품 기한 → 항목 가드.
 * 환불은 Payment FOR UPDATE + INSPECTING 멱등 + claim 별 멱등 키로 다중 부분 반품을 직렬화한다.
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

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
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    /** 배송완료 anchor 로부터 이 기간 내에만 반품 접수 허용. */
    private final Duration returnWindow;

    public ClaimService(ClaimRepository claimRepository,
                        OrderRepository orderRepository,
                        PaymentRepository paymentRepository,
                        PaymentGateway paymentGateway,
                        CouponApplicationService couponApplicationService,
                        ShippingService shippingService,
                        AlbumRepository albumRepository,
                        MemberRepository memberRepository,
                        ApplicationEventPublisher eventPublisher,
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
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.returnWindow = returnWindow;
    }

    @Transactional
    public Claim request(ClaimCreateCommand command) {
        Order order = loadOwnedOrderForUpdate(command);
        requireActiveMember(command.memberId());
        requireReturnableStatus(order);
        requireWithinReturnWindow(order);
        requireNonEmptyLines(command);

        Map<Long, Integer> requested = mergeRequestedQuantities(command.lines());
        Claim claim = Claim.request(order, command.reason());
        addValidatedItems(claim, orderItemsById(order), requested, claimedQuantitiesByOrderItem(order.getId()));
        Claim saved = claimRepository.save(claim);
        log.info("반품 접수 — claimId={}, order={}, 항목 {}건", saved.getId(), order.getOrderNumber(), requested.size());
        return saved;
    }

    /** 주문 행을 PESSIMISTIC_WRITE 로 잠가 동시 접수를 직렬화하고 본인 주문임을 확인. */
    private Order loadOwnedOrderForUpdate(ClaimCreateCommand command) {
        Order order = orderRepository.findByOrderNumberForUpdate(command.orderNumber())
                .orElseThrow(OrderNotFoundException::new);
        if (command.memberId() == null || !Objects.equals(order.getMemberId(), command.memberId())) {
            throw new OrderNotFoundException();
        }
        return order;
    }

    /** 탈퇴(soft delete) 회원이 만료 전 access 토큰으로 반품 접수하는 것을 차단. */
    private void requireActiveMember(Long memberId) {
        if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new MemberNotFoundException();
        }
    }

    private void requireReturnableStatus(Order order) {
        if (!OrderStatus.DELIVERED_OR_COMPLETED.contains(order.getStatus())) {
            throw new OrderNotReturnableException(order.getStatus());
        }
    }

    /**
     * 기한 anchor 는 결정적 배송완료 시각이어야 한다. order.deliveredAt(changeStatus 가 항상 기록)을 1차로,
     * delivered_at 컬럼 도입 전 레거시만 shipping.deliveredAt 로 보강한다. 둘 다 없으면 거부(updatedAt 폴백은 비결정적이라 배제).
     */
    private void requireWithinReturnWindow(Order order) {
        Instant deliveredAt = Optional.ofNullable(order.getDeliveredAt())
                .or(() -> shippingService.findDeliveredAt(order.getId()))
                .orElseThrow(ReturnWindowNotDeterminableException::new);
        if (clock.instant().isAfter(deliveredAt.plus(returnWindow))) {
            throw new ReturnWindowExpiredException();
        }
    }

    private void requireNonEmptyLines(ClaimCreateCommand command) {
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new EmptyClaimException();
        }
    }

    /**
     * 관리자 발송 전 부분 취소 — CANCEL 클레임을 즉시 환불 확정한다(회수·검수 없음).
     *
     * 쿠폰 정책: 부분취소 후 잔여 정가 ≥ 최소주문금액이면 할인 안분(쿠폰 USED 유지), 미만이면 쿠폰을 무효화해 적용
     * 할인분을 환불에서 제외하고 복원한다. 단 적용 할인이 취소 품목 정가를 초과하면 무효화 않고 안분으로 폴백.
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

        // 항목 검증을 결제 락 획득 전에 먼저.
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

        // 전량 취소 = 모든 항목의 (기클레임 + 이번 요청) 수량이 주문 수량에 도달.
        boolean fullyCancelled = order.getItems().stream().allMatch(item ->
                alreadyClaimed.getOrDefault(item.getId(), 0) + requested.getOrDefault(item.getId(), 0)
                        >= item.getQuantity());

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
            order.changeStatus(OrderStatus.CANCELLED, command.reason(), now);
            shippingService.cancelForOrder(order.getId());
        }
        saved.markCancelRefunded(refund.amount(), now);
        log.info("부분 취소 환불 — claimId={}, order={}, 환불액={}, 쿠폰무효={}, 전량취소={}, 결제상태={}",
                saved.getId(), order.getOrderNumber(), refund.amount(), refund.voidCoupon(), fullyCancelled,
                payment.getStatus());
        return saved;
    }

    @Transactional
    public Claim approve(Long claimId) {
        Claim claim = claimRepository.findByIdForUpdate(claimId).orElseThrow(ClaimNotFoundException::new);
        claim.approve(clock.instant());
        log.info("반품 승인 — claimId={}", claimId);
        return claim;
    }

    /** 거부는 재입고·환불 없음. FOR UPDATE 로 동시 completeRefund 와 직렬화. */
    @Transactional
    public Claim reject(Long claimId, String reason) {
        Claim claim = claimRepository.findByIdForUpdate(claimId).orElseThrow(ClaimNotFoundException::new);
        claim.reject(reason, clock.instant());
        log.info("반품 거부 — claimId={}, status(before)={}", claimId, claim.getStatus());
        return claim;
    }

    /** 스케줄러 위임. 상태 불일치면 무시. */
    @Transactional
    public void advanceToInTransit(Long claimId) {
        claimRepository.findByIdForUpdate(claimId).ifPresent(claim -> {
            if (claim.getStatus() == ClaimStatus.APPROVED) {
                claim.startTransit(clock.instant());
            }
        });
    }

    /** 스케줄러 위임. 상태 불일치면 무시. */
    @Transactional
    public void advanceToInspecting(Long claimId) {
        claimRepository.findByIdForUpdate(claimId).ifPresent(claim -> {
            if (claim.getStatus() == ClaimStatus.IN_TRANSIT) {
                claim.startInspecting(clock.instant());
            }
        });
    }

    /**
     * 검수 통과 환불 (스케줄러 자동통과 또는 관리자 수동). INSPECTING 이 아니면 no-op 으로 멱등.
     * 누적 환불액이 결제 전액에 도달할 때만 쿠폰 복원 + 주문 반품 마커.
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
        restoreClaimStock(claim);
        boolean fullyReturned = payment.getStatus() == PaymentStatus.REFUNDED;
        if (fullyReturned) {
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
     * 비례 배분 환불 증분 = 이번 누적 목표 − 기환불액. 누적 반품 정가 비율만큼의 실결제액을 목표로 잡는다(BigInteger 반올림).
     */
    static long proportionalRefund(long payable, long totalGross, long cumGrossIncl, long alreadyRefunded) {
        long remaining = payable - alreadyRefunded;
        if (remaining <= 0) {
            return 0; // 환불 가능액 소진
        }
        long target = totalGross == 0 ? payable
                : BigInteger.valueOf(payable).multiply(BigInteger.valueOf(cumGrossIncl))
                        .add(BigInteger.valueOf(totalGross / 2))
                        .divide(BigInteger.valueOf(totalGross))
                        .longValueExact();
        long increment = target - alreadyRefunded;
        // 0 이하면 최소 1원으로 전진(정체 방지).
        return increment > 0 ? Math.min(increment, remaining) : 1;
    }

    /**
     * 환불 증분 + 쿠폰 무효 여부 (취소/반품 공용). 세 분기:
     * - 쿠폰 미적용 → 이번 클레임 정가만 환불.
     * - 취소 무효(잔여 정가 < 최소주문금액) → 누적 목표 = cumGrossIncl − discount, 호출 측이 복원. 증분 0 이하면 비례 폴백.
     * - 그 외 → 비례 배분.
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
            // 증분 0 이하 → 비례 폴백.
        }
        return new RefundComputation(proportionalRefund(payable, totalGross, cumGrossIncl, alreadyRefunded), false);
    }

    record RefundComputation(long amount, boolean voidCoupon) {
    }

    private void callGatewayRefund(Payment payment, Claim claim, long amount) {
        // claim 별 결정적 멱등 키로 PG 재시도 중복 환불 방지.
        RefundRequest request = new RefundRequest(
                payment.getPgTransactionId(), amount, claim.getReason(), payment.refundIdempotencyKey(claim.getId()));
        GatewayRefunds.refund(paymentGateway, request);
    }

    /** 환불액이 양수일 때만 PG 호출 + 결제 누적 (취소/반품 공용). */
    private void settleClaimRefund(Payment payment, Claim claim, long amount, Instant now) {
        if (amount > 0) {
            callGatewayRefund(payment, claim, amount);
            payment.refund(amount, now);
        }
    }

    /** 원자적 가산 UPDATE 로 재입고 (취소/반품 공용). */
    private void restoreClaimStock(Claim claim) {
        Map<Long, Integer> quantityByAlbumId = claim.getItems().stream()
                .collect(Collectors.groupingBy(item -> item.getOrderItem().getAlbum().getId(),
                        Collectors.summingInt(ClaimItem::getQuantity)));
        StockRestorer.restore(albumRepository, quantityByAlbumId);
        // 재입고된 album 들의 조회 캐시(상세/랜딩) 무효화.
        if (!quantityByAlbumId.isEmpty()) {
            eventPublisher.publishEvent(new AlbumStockChangedEvent(quantityByAlbumId.keySet()));
        }
    }

    private static Map<Long, OrderItem> orderItemsById(Order order) {
        Map<Long, OrderItem> map = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            map.put(item.getId(), item);
        }
        return map;
    }

    private static Map<Long, Integer> mergeRequestedQuantities(List<ClaimCreateCommand.Line> lines) {
        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (ClaimCreateCommand.Line line : lines) {
            requested.merge(line.orderItemId(), line.quantity(), Integer::sum);
        }
        return requested;
    }

    /** 주문 소속·잔여 초과를 가드한 뒤 ClaimItem 으로 추가 (취소/반품 공용). */
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

    /** OrderItem 별 기클레임 수량 — 거부(REJECTED)는 제외해 재요청을 허용. */
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
