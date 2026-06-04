package com.groove.payment.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 결제를 찾을 수 없는 경우. HTTP 404.
 *
 * <p>실제로 없는 경우뿐 아니라 타 회원·게스트 주문의 결제에 접근하는 경우도 존재 노출 회피를 위해
 * 동일하게 404 로 통일한다 ({@code OrderNotFoundException} 과 같은 패턴).
 */
public class PaymentNotFoundException extends DomainException {

    public PaymentNotFoundException() {
        super(ErrorCode.PAYMENT_NOT_FOUND);
    }
}
