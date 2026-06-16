package com.groove.member.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 활성 회원을 찾을 수 없는 경우. HTTP 404. */
public class MemberNotFoundException extends DomainException {

    public MemberNotFoundException() {
        super(ErrorCode.MEMBER_NOT_FOUND);
    }
}
