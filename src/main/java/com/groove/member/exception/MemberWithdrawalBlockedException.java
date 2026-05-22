package com.groove.member.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 진행 중인 주문(PAID·PREPARING·SHIPPED)이 남아 회원 탈퇴를 차단하는 경우. HTTP 409 (#78).
 *
 * <p>탈퇴는 soft delete 이고 주문 이력은 보존되지만, 배송이 끝나지 않은 주문을 둔 채 계정을 닫으면
 * 배송·환불의 책임 주체가 사라진다. 종착에 이르지 않은 주문이 모두 정리(배송 완료/취소)된 뒤에만
 * 탈퇴를 허용한다.
 */
public class MemberWithdrawalBlockedException extends DomainException {

    public MemberWithdrawalBlockedException() {
        super(ErrorCode.MEMBER_WITHDRAWAL_BLOCKED);
    }
}
