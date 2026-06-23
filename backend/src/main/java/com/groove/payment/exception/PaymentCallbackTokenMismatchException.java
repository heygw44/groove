package com.groove.payment.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 토스 successUrl/failUrl 콜백의 토큰이 결제에 저장된 callback_token 과 일치하지 않는 경우. HTTP 403.
 *
 * <p>success/fail 은 토스가 보내는 미인증 브라우저 GET 이라 orderNumber 만 알면 호출 가능하다(#295 리뷰 P1-1).
 * checkout 에서 발급한 결제별 토큰을 검증해 타인의 진행 중 결제에 대한 교차 주문 조작을 차단한다(#304).
 * 컨트롤러는 이 예외를 fail 리다이렉트로 흡수하므로 클라이언트에 상세가 노출되지 않는다.
 */
public class PaymentCallbackTokenMismatchException extends DomainException {

    public PaymentCallbackTokenMismatchException(String orderNumber) {
        super(ErrorCode.PAYMENT_CALLBACK_TOKEN_MISMATCH, "결제 콜백 토큰 불일치 order=" + orderNumber);
    }
}
