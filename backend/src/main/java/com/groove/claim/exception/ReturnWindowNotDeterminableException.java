package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 배송완료 시각을 알 수 없어 반품 기한을 산정할 수 없는 경우. HTTP 422.
 *
 * <p>주문이 DELIVERED/COMPLETED 인데도 배송 행이 없거나 {@code Shipping.deliveredAt} 이 비어 있는 비정상 상태에서
 * 던진다 (#239) — NPE(500) 대신 명시적으로 거부해 원인을 드러낸다.
 */
public class ReturnWindowNotDeterminableException extends DomainException {

    public ReturnWindowNotDeterminableException() {
        super(ErrorCode.CLAIM_WINDOW_NOT_DETERMINABLE);
    }
}
