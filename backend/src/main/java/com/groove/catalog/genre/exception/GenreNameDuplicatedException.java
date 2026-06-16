package com.groove.catalog.genre.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 장르 이름이 이미 존재하는 경우. 409 응답으로 매핑된다.
 */
public class GenreNameDuplicatedException extends DomainException {

    public GenreNameDuplicatedException() {
        super(ErrorCode.GENRE_NAME_DUPLICATED);
    }

    public GenreNameDuplicatedException(Throwable cause) {
        super(ErrorCode.GENRE_NAME_DUPLICATED, ErrorCode.GENRE_NAME_DUPLICATED.getDefaultMessage(), cause);
    }
}
