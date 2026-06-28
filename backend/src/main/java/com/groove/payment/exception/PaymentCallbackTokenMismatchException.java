package com.groove.payment.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 토스 콜백 토큰이 결제에 저장된 callback_token 과 불일치. HTTP 403.
 * success 콜백 컨트롤러는 이 예외를 흡수해 302 fail 리다이렉트로 응답한다(JSON 미노출). 403 은 다른 경로의 분류값일 뿐.
 * 미인증 브라우저 GET 이라 orderNumber 만 알면 호출 가능하므로, checkout 이 발급한 결제별 토큰으로 교차 주문 조작을 차단한다.
 */
public class PaymentCallbackTokenMismatchException extends DomainException {

    public PaymentCallbackTokenMismatchException(String orderNumber) {
        super(ErrorCode.PAYMENT_CALLBACK_TOKEN_MISMATCH, "결제 콜백 토큰 불일치 order=" + orderNumber);
    }
}
