package com.groove.member.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 회원가입 시 이미 사용 중인 이메일이 입력된 경우. 탈퇴(soft delete) 회원의 이메일도 점유한다. */
public class MemberEmailDuplicatedException extends DomainException {

    public MemberEmailDuplicatedException() {
        super(ErrorCode.MEMBER_EMAIL_DUPLICATED);
    }

    public MemberEmailDuplicatedException(Throwable cause) {
        super(ErrorCode.MEMBER_EMAIL_DUPLICATED, ErrorCode.MEMBER_EMAIL_DUPLICATED.getDefaultMessage(), cause);
    }
}
