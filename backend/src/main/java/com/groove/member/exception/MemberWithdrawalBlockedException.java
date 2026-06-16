package com.groove.member.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 진행 중인 주문(PAID·PREPARING·SHIPPED)이 남아 회원 탈퇴를 차단하는 경우. HTTP 409. */
public class MemberWithdrawalBlockedException extends DomainException {

    public MemberWithdrawalBlockedException() {
        super(ErrorCode.MEMBER_WITHDRAWAL_BLOCKED);
    }
}
