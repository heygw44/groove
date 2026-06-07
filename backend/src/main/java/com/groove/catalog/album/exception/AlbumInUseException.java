package com.groove.catalog.album.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 삭제하려는 앨범이 cart_item / order_item 에서 참조 중인 경우. 409 응답으로 매핑된다.
 *
 * <p>cart_item.album_id(V7) · order_item.album_id(V8) 는 ON DELETE RESTRICT FK 다.
 * 사전 검사({@code CartRepository.existsByAlbumId} · {@code OrderRepository.existsByAlbumId})
 * 가 1차로 잡고, 동시 INSERT 로 인한 {@link org.springframework.dao.DataIntegrityViolationException}
 * 도 본 예외로 변환해 응답을 일원화한다. 정책은
 * {@link com.groove.catalog.artist.exception.ArtistInUseException} 과 동일하다.
 */
public class AlbumInUseException extends DomainException {

    public AlbumInUseException() {
        super(ErrorCode.ALBUM_IN_USE);
    }

    public AlbumInUseException(Throwable cause) {
        super(ErrorCode.ALBUM_IN_USE, ErrorCode.ALBUM_IN_USE.getDefaultMessage(), cause);
    }
}
