package com.groove.catalog.artist.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 요청한 아티스트 ID 가 존재하지 않는 경우. 404 응답으로 매핑된다. */
public class ArtistNotFoundException extends DomainException {

    public ArtistNotFoundException() {
        super(ErrorCode.ARTIST_NOT_FOUND);
    }
}
