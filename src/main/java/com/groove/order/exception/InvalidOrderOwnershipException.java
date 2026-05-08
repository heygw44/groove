package com.groove.order.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 주문 소유 형식이 올바르지 않은 경우. HTTP 422.
 *
 * <p>{@code member_id XOR guest_email} 규칙 위반:
 * <ul>
 *   <li>회원 + 게스트 이메일이 동시에 채워진 경우</li>
 *   <li>둘 다 비어 있는 경우</li>
 * </ul>
 *
 * <p>DB 제약은 두지 않고 도메인/서비스 레이어에서만 검증한다 (ERD §4.9 [APP] 표기).
 */
public class InvalidOrderOwnershipException extends DomainException {

    public InvalidOrderOwnershipException() {
        super(ErrorCode.ORDER_INVALID_OWNERSHIP);
    }
}
