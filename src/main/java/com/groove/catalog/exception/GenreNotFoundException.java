package com.groove.catalog.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 요청한 장르 ID 가 존재하지 않는 경우. 404 응답으로 매핑된다.
 *
 * <p>예외 메시지에 ID 를 노출하지 않는다 — 클라이언트 응답은 {@link ErrorCode#GENRE_NOT_FOUND}
 * 의 기본 메시지로 일원화되며, 디버깅용 ID 는 서버 로그에서 확인한다.
 */
public class GenreNotFoundException extends DomainException {

    public GenreNotFoundException() {
        super(ErrorCode.GENRE_NOT_FOUND);
    }
}
