package com.groove.member.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 회원가입 시 이미 사용 중인 이메일이 입력된 경우.
 *
 * <p>패턴 A — 탈퇴(soft delete) 회원의 이메일도 점유한다. 어뷰징 방지를 우선한 결정.
 * 회원 탈퇴 이슈 진입 시 재검토 (이메일 익명화 또는 cooldown 정책).
 *
 * <p>예외 메시지에는 이메일을 포함하지 않는다 (PII 로그 노출 차단).
 * 클라이언트 응답은 {@link ErrorCode#MEMBER_EMAIL_DUPLICATED} 의 기본 메시지로 일원화된다.
 */
public class MemberEmailDuplicatedException extends DomainException {

    public MemberEmailDuplicatedException() {
        super(ErrorCode.MEMBER_EMAIL_DUPLICATED);
    }

    public MemberEmailDuplicatedException(Throwable cause) {
        super(ErrorCode.MEMBER_EMAIL_DUPLICATED, ErrorCode.MEMBER_EMAIL_DUPLICATED.getDefaultMessage(), cause);
    }
}
