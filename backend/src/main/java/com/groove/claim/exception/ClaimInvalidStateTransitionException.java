package com.groove.claim.exception;

import com.groove.claim.domain.ClaimStatus;
import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 허용되지 않은 반품 상태 전이를 시도한 경우. HTTP 409.
 *
 * <p>관리자 승인/거부/환불이나 스케줄러 자동 진행이 현재 상태에서 불가능한 전이를 시도하면 던진다 (#239) —
 * {@code OrderStatus}/{@code ShippingStatus} 와 달리 관리자 API 가 직접 트리거하므로 500 이 아니라 409 로 매핑한다.
 */
public class ClaimInvalidStateTransitionException extends DomainException {

    public ClaimInvalidStateTransitionException(ClaimStatus from, ClaimStatus to) {
        super(ErrorCode.CLAIM_INVALID_STATE_TRANSITION, "허용되지 않은 반품 상태 전이입니다: " + from + " -> " + to);
    }
}
