package com.groove.catalog.genre.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 삭제하려는 장르가 album 에서 참조 중인 경우. 409 응답으로 매핑된다.
 *
 * <p>Album 도입(W5-3) 이후 ON DELETE RESTRICT FK 가 활성화된다. 정책은
 * {@link com.groove.catalog.artist.exception.ArtistInUseException} 과 동일하다.
 */
public class GenreInUseException extends DomainException {

    public GenreInUseException() {
        super(ErrorCode.GENRE_IN_USE);
    }

    public GenreInUseException(Throwable cause) {
        super(ErrorCode.GENRE_IN_USE, ErrorCode.GENRE_IN_USE.getDefaultMessage(), cause);
    }
}
