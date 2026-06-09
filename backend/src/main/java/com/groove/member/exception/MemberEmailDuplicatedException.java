package com.groove.member.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 회원가입 시 이미 사용 중인 이메일이 입력된 경우.
 *
 * <p>패턴 A — 탈퇴(soft delete) 회원의 이메일도 점유한다. 어뷰징 방지를 우선한 결정. #170 에서 탈퇴 시
 * 이메일 평문을 제거(익명화)하되 점유는 {@code email_hash}(정규화 이메일의 해시)로 옮겨 유지하므로,
 * 평문 노출 없이도 재가입 차단이 그대로 성립한다. #186 에서 그 해시를 결정적 SHA-256 →
 * 서버 비밀키 HMAC({@link com.groove.member.security.EmailHasher}) 으로 전환했다.
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
