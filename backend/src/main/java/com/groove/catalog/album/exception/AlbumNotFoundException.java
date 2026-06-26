package com.groove.catalog.album.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 요청한 앨범 ID 가 존재하지 않는 경우. 404 응답으로 매핑된다. */
public class AlbumNotFoundException extends DomainException {

    public AlbumNotFoundException() {
        super(ErrorCode.ALBUM_NOT_FOUND);
    }
}
