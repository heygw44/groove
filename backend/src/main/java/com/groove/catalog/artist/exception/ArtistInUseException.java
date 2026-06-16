package com.groove.catalog.artist.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 삭제하려는 아티스트가 album 에서 참조 중인 경우. 409 응답으로 매핑된다. */
public class ArtistInUseException extends DomainException {

    public ArtistInUseException() {
        super(ErrorCode.ARTIST_IN_USE);
    }

    public ArtistInUseException(Throwable cause) {
        super(ErrorCode.ARTIST_IN_USE, ErrorCode.ARTIST_IN_USE.getDefaultMessage(), cause);
    }
}
