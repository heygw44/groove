package com.groove.member.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 활성 회원을 찾을 수 없는 경우. HTTP 404.
 *
 * <p>{@code /members/me} 컨텍스트: 유효한 access token 을 보유했지만 해당 회원이 soft delete 되어
 * {@code findByIdAndDeletedAtIsNull} 결과가 비는 경우 발생한다 (탈퇴 후 토큰 만료 전 윈도, #78 연계).
 * 인증 자체는 통과했고 리소스만 부재하므로 401 이 아니라 404 가 맞다.
 */
public class MemberNotFoundException extends DomainException {

    public MemberNotFoundException() {
        super(ErrorCode.MEMBER_NOT_FOUND);
    }
}
