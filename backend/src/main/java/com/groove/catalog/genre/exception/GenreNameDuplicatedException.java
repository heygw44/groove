package com.groove.catalog.genre.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 장르 이름이 이미 존재하는 경우. 409 응답으로 매핑된다.
 *
 * <p>create 시 {@code existsByName} 선검사가 1차로 잡고, 동시 INSERT 로 인한
 * {@code DataIntegrityViolationException} 도 본 예외로 변환해 응답을 일원화한다.
 */
public class GenreNameDuplicatedException extends DomainException {

    public GenreNameDuplicatedException() {
        super(ErrorCode.GENRE_NAME_DUPLICATED);
    }

    public GenreNameDuplicatedException(Throwable cause) {
        super(ErrorCode.GENRE_NAME_DUPLICATED, ErrorCode.GENRE_NAME_DUPLICATED.getDefaultMessage(), cause);
    }
}
