package com.groove.catalog.album.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 요청한 앨범 ID 가 존재하지 않는 경우. 404 응답으로 매핑된다.
 *
 * <p>다른 카탈로그 NotFound 와 동일하게 클라이언트 응답은 {@link ErrorCode#ALBUM_NOT_FOUND}
 * 의 기본 메시지로 일원화된다 — ID 노출 금지.
 */
public class AlbumNotFoundException extends DomainException {

    public AlbumNotFoundException() {
        super(ErrorCode.ALBUM_NOT_FOUND);
    }
}
