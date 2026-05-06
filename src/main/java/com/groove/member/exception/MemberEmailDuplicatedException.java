package com.groove.member.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 회원가입 시 이미 사용 중인 이메일이 입력된 경우.
 *
 * <p>패턴 A — 탈퇴(soft delete) 회원의 이메일도 점유한다. 어뷰징 방지를 우선한 결정.
 * 회원 탈퇴 이슈 진입 시 재검토 (이메일 익명화 또는 cooldown 정책).
 */
public class MemberEmailDuplicatedException extends DomainException {

    public MemberEmailDuplicatedException(String email) {
        super(ErrorCode.MEMBER_EMAIL_DUPLICATED, "이미 사용 중인 이메일입니다: " + email);
    }
}
