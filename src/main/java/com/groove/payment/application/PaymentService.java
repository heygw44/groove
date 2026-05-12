package com.groove.payment.application;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.payment.api.dto.PaymentApiResponse;
import com.groove.payment.api.dto.PaymentCreateRequest;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.exception.PaymentGatewayException;
import com.groove.payment.exception.PaymentNotFoundException;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.PaymentRequest;
import com.groove.payment.gateway.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 요청 접수 트랜잭션 경계 (#W7-3, API.md §3.6).
 *
 * <h2>멱등 실행 규약</h2>
 * <p>{@link #requestPayment} 는 {@link com.groove.common.idempotency.IdempotencyService#execute} 의
 * {@code action} 으로 호출되는 것을 전제로 한다 — 따라서 자기 트랜잭션({@code @Transactional})을 관리하고
 * 반환 전에 커밋한다. 호출자(컨트롤러)는 비트랜잭션이어야 한다 (그래야 "action 커밋 → 멱등성 마커
 * COMPLETED 커밋" 순서가 보장된다 — {@code IdempotencyService} 의 호출 규약 참조).
 *
 * <h2>처리 순서</h2>
 * <ol>
 *   <li>주문 로딩 + 회원 주문이면 호출자 소유 검증 (불일치/익명 → {@link OrderNotFoundException} 404).</li>
 *   <li>주문에 이미 접수된 결제가 있으면 그대로 반환 (주문 레벨 멱등 — {@code uk_payment_order} 충돌 사전 회피).</li>
 *   <li>주문 상태가 PENDING 아니면 409 ({@link IllegalStateTransitionException}, API.md — {@code ORDER_INVALID_STATE_TRANSITION}).</li>
 *   <li>결제 금액이 0 이하면 422 ({@code DomainException}, {@code DOMAIN_RULE_VIOLATION}).</li>
 *   <li>PG {@code request()} 호출 — 실패 시 {@link PaymentGatewayException} (502).</li>
 *   <li>{@code Payment(PENDING)} 저장 후 응답 DTO 반환.</li>
 * </ol>
 *
 * <p>결제 확정(PAID/FAILED 전이, {@code paidAt} 기록, 주문 상태 전이)은 #W7-4 웹훅/폴링 범위다 —
 * 본 서비스는 주문 상태를 변경하지 않는다.
 *
 * <h2>알려진 한계</h2>
 * <p>PG {@code request()} 호출이 {@code @Transactional} 안에서 일어나 (Mock 처리 지연 동안) DB 커넥션을
 * 점유한다 — v1 Mock 지연은 짧아 허용한다. 실 PG 도입 시 PG 호출과 영속화를 분리하는 것을 재검토한다.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          PaymentGateway paymentGateway) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
    }

    /**
     * 결제 요청 접수. {@code callerMemberId} 는 인증 회원의 id (게스트/익명이면 {@code null}).
     */
    @Transactional
    public PaymentApiResponse requestPayment(Long callerMemberId, PaymentCreateRequest request) {
        Order order = orderRepository.findByOrderNumber(request.orderNumber())
                .orElseThrow(OrderNotFoundException::new);
        if (!canRequestPaymentFor(order, callerMemberId)) {
            // 타 회원 주문 또는 회원 주문에 익명 접근 — 존재 노출 회피를 위해 404 로 통일.
            throw new OrderNotFoundException();
        }

        Payment existing = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (existing != null) {
            // 같은 주문에 이미 접수된 결제가 있으면 그대로 반환한다(주문 레벨 멱등). 본 이슈에선 결제가 항상
            // PENDING 이라 무해하지만, #W7-4 이후 PAID/FAILED 로 확정된 결제에 재요청이 와도 상태와 무관하게
            // 그 결제를 돌려준다 — 그 시점에 "이미 처리된 결제" 분기를 따로 둘지 재검토할 것.
            return PaymentApiResponse.from(existing);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.PAID);
        }
        if (order.getTotalAmount() <= 0) {
            throw new DomainException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "결제할 금액이 없는 주문입니다: " + order.getOrderNumber());
        }

        PaymentResponse pgResponse = callGateway(order);
        Payment payment = paymentRepository.save(Payment.initiate(
                order, order.getTotalAmount(), request.method(), pgResponse.provider(), pgResponse.pgTransactionId()));
        log.info("결제 접수: paymentId={}, order={}, amount={}, method={}, pgTx={}",
                payment.getId(), order.getOrderNumber(), payment.getAmount(), payment.getMethod(),
                payment.getPgTransactionId());
        return PaymentApiResponse.from(payment);
    }

    /**
     * 회원 본인 주문의 결제 단건 조회 (API.md §3.6).
     *
     * <p>타 회원/게스트 주문의 결제는 존재 노출 회피를 위해 {@link PaymentNotFoundException}(404) 으로 통일한다.
     */
    @Transactional(readOnly = true)
    public PaymentApiResponse findForMember(Long memberId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        if (!isOwnedByMember(payment.getOrder(), memberId)) {
            throw new PaymentNotFoundException();
        }
        return PaymentApiResponse.from(payment);
    }

    /** 게스트 주문은 익명 호출자도 결제 시작 가능 (API.md — POST /payments 는 Public). 회원 주문은 본인만. */
    private boolean canRequestPaymentFor(Order order, Long callerMemberId) {
        if (order.isGuestOrder()) {
            return true;
        }
        return order.getMemberId().equals(callerMemberId);
    }

    /** GET /payments/{id} 는 회원 전용 — 회원 본인이 소유한 주문의 결제만 노출한다. */
    private boolean isOwnedByMember(Order order, Long memberId) {
        return !order.isGuestOrder() && order.getMemberId().equals(memberId);
    }

    private PaymentResponse callGateway(Order order) {
        PaymentRequest pgRequest = new PaymentRequest(order.getOrderNumber(), order.getTotalAmount());
        try {
            return paymentGateway.request(pgRequest);
        } catch (RuntimeException gatewayFailure) {
            throw new PaymentGatewayException(gatewayFailure);
        }
    }
}
