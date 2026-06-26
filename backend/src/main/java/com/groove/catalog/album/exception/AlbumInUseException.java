package com.groove.catalog.album.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 삭제하려는 앨범이 cart_item / order_item 에서 참조 중인 경우. 409 응답으로 매핑된다. */
public class AlbumInUseException extends DomainException {

    public AlbumInUseException() {
        super(ErrorCode.ALBUM_IN_USE);
    }

    public AlbumInUseException(Throwable cause) {
        super(ErrorCode.ALBUM_IN_USE, ErrorCode.ALBUM_IN_USE.getDefaultMessage(), cause);
    }
}
