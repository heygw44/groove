package com.groove.payment.application;

import com.groove.order.domain.Order;
import com.groove.payment.api.dto.PaymentApiResponse;
import com.groove.payment.api.dto.PaymentCreateRequest;
import com.groove.payment.application.PaymentRequestSteps.PaymentRequestPrep;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.exception.PaymentGatewayException;
import com.groove.payment.exception.PaymentNotFoundException;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.PaymentRequest;
import com.groove.payment.gateway.PaymentResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 요청 접수 오케스트레이터 (#W7-3, API.md §3.6 / #237).
 *
 * <p>{@code requestPayment} 는 IdempotencyService.execute 의 action 으로 호출되는 것을 전제로 한다 —
 * 컨트롤러는 비트랜잭션이어야 "action 커밋 → 멱등성 마커 COMPLETED 커밋" 순서가 보장된다. 이 메서드 자체는
 * 트랜잭션을 열지 않고, 트랜잭션 경계를 갖는 두 단계({@link PaymentRequestSteps#prepare 검증},
 * {@link PaymentRequestSteps#persist 영속화})를 협력 빈에 위임한다. 각 단계가 반환 전 커밋하므로 멱등 실행
 * 규약은 그대로 유지된다.
 *
 * <p><b>PG 호출의 트랜잭션 분리 (#237)</b>: 상태 검증(readOnly tx) → PG {@code request()}(트랜잭션 밖) →
 * Payment(PENDING) 영속화(tx) 로 나눠 PG 호출 동안 DB 커넥션을 점유하지 않는다. 기존 한계(PG request() 가
 * {@code @Transactional} 안에서 Mock 처리 지연 동안 커넥션 점유)를 제거한다 — 실 PG 전환 시 커넥션 풀 고갈 방지.
 * PG 호출 실패 시점에는 아직 Payment 가 영속화되지 않았으므로 보상이 필요 없고 {@link PaymentGatewayException}(502)
 * 만 전파한다.
 *
 * <p>결제 확정(PAID/FAILED 전이, paidAt 기록, 주문 상태 전이)은 #W7-4 웹훅/폴링 범위
 * ({@code PaymentCallbackService})이며 본 서비스는 주문 상태를 바꾸지 않는다.
 */
@Service
public class PaymentService {

    private final PaymentRequestSteps steps;
    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRequestSteps steps,
                          PaymentGateway paymentGateway,
                          PaymentRepository paymentRepository) {
        this.steps = steps;
        this.paymentGateway = paymentGateway;
        this.paymentRepository = paymentRepository;
    }

    /** 결제 요청 접수. callerMemberId 는 인증 회원의 id (게스트/익명이면 null). */
    public PaymentApiResponse requestPayment(Long callerMemberId, PaymentCreateRequest request) {
        PaymentRequestPrep prep = steps.prepare(callerMemberId, request);
        if (prep.isExisting()) {
            return prep.existingResponse();
        }
        // PG 호출은 트랜잭션 밖 — prepare 의 readOnly 트랜잭션이 이미 커밋(커넥션 해제)됐고, persist 가 새 트랜잭션을 연다.
        PaymentResponse pgResponse = callGateway(prep.orderNumber(), prep.payable());
        try {
            return steps.persist(prep, request.method(), pgResponse);
        } catch (DataIntegrityViolationException duplicate) {
            // prepare/persist 사이에 동시 다른 멱등 키로 같은 주문 결제가 접수돼 uk_payment_order 충돌 —
            // 새 트랜잭션에서 기존 결제를 재조회해 주문 레벨 멱등으로 응답한다(충돌 트랜잭션은 rollback-only 라 분리 필수).
            return steps.findExistingForOrder(prep.orderId())
                    .orElseThrow(() -> duplicate);
        }
    }

    /**
     * 회원 본인 주문의 결제 단건 조회 (API.md §3.6).
     * 타 회원/게스트 주문의 결제는 존재 노출 회피를 위해 PaymentNotFoundException(404) 으로 통일한다.
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

    /** GET /payments/{id} 는 회원 전용 — 회원 본인이 소유한 주문의 결제만 노출한다. */
    private boolean isOwnedByMember(Order order, Long memberId) {
        return !order.isGuestOrder() && order.getMemberId().equals(memberId);
    }

    private PaymentResponse callGateway(String orderNumber, long payableAmount) {
        // PG 청구액은 payable (#91) — 쿠폰 할인 반영 후의 실제 결제 금액.
        PaymentRequest pgRequest = new PaymentRequest(orderNumber, payableAmount);
        try {
            return paymentGateway.request(pgRequest);
        } catch (RuntimeException gatewayFailure) {
            throw new PaymentGatewayException(gatewayFailure);
        }
    }
}
