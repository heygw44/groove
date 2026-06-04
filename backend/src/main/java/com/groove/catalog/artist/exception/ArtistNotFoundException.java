package com.groove.catalog.artist.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 요청한 아티스트 ID 가 존재하지 않는 경우. 404 응답으로 매핑된다.
 *
 * <p>Genre/Label 과 동일하게 예외 메시지에 ID 를 노출하지 않는다 — 클라이언트 응답은
 * {@link ErrorCode#ARTIST_NOT_FOUND} 의 기본 메시지로 일원화된다.
 */
public class ArtistNotFoundException extends DomainException {

    public ArtistNotFoundException() {
        super(ErrorCode.ARTIST_NOT_FOUND);
    }
}
