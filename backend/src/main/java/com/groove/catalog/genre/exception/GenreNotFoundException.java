package com.groove.catalog.genre.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 요청한 장르 ID 가 존재하지 않는 경우. 404 응답으로 매핑된다.
 */
public class GenreNotFoundException extends DomainException {

    public GenreNotFoundException() {
        super(ErrorCode.GENRE_NOT_FOUND);
    }
}
