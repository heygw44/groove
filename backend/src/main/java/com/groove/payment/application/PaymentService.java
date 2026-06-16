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
 * 결제 요청 접수 오케스트레이터.
 * 상태 검증(readOnly tx) → PG request()(트랜잭션 밖) → Payment(PENDING) 영속화(tx) 의 세 단계를 협력 빈에 위임한다.
 * PG 호출 실패 시 PaymentGatewayException(502) 만 전파한다.
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
        // PG 호출은 트랜잭션 밖.
        PaymentResponse pgResponse = callGateway(prep.orderNumber(), prep.payable());
        try {
            return steps.persist(prep, request.method(), pgResponse);
        } catch (DataIntegrityViolationException duplicate) {
            // uk_payment_order 충돌 시 새 트랜잭션에서 기존 결제를 재조회해 주문 레벨 멱등으로 응답한다.
            return steps.findExistingForOrder(prep.orderId())
                    .orElseThrow(() -> duplicate);
        }
    }

    /** 회원 본인 주문의 결제 단건 조회. 타 회원/게스트 주문은 PaymentNotFoundException(404). */
    @Transactional(readOnly = true)
    public PaymentApiResponse findForMember(Long memberId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        if (!isOwnedByMember(payment.getOrder(), memberId)) {
            throw new PaymentNotFoundException();
        }
        return PaymentApiResponse.from(payment);
    }

    /** 회원 본인이 소유한 주문인지 판정. */
    private boolean isOwnedByMember(Order order, Long memberId) {
        return !order.isGuestOrder() && order.getMemberId().equals(memberId);
    }

    private PaymentResponse callGateway(String orderNumber, long payableAmount) {
        // PG 청구액은 payable(쿠폰 할인 반영 후 결제 금액).
        PaymentRequest pgRequest = new PaymentRequest(orderNumber, payableAmount);
        try {
            return paymentGateway.request(pgRequest);
        } catch (RuntimeException gatewayFailure) {
            throw new PaymentGatewayException(gatewayFailure);
        }
    }
}
