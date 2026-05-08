package com.groove.catalog.artist.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 삭제하려는 아티스트가 album 에서 참조 중인 경우. 409 응답으로 매핑된다.
 *
 * <p>Album 도입(W5-3) 이후 ON DELETE RESTRICT FK 가 활성화되며, 사전 검사
 * ({@code AlbumRepository.existsByArtist_Id}) 가 1차로 잡고 동시 INSERT 로 인한
 * {@link org.springframework.dao.DataIntegrityViolationException} 도 본 예외로 변환해 응답을 일원화한다.
 */
public class ArtistInUseException extends DomainException {

    public ArtistInUseException() {
        super(ErrorCode.ARTIST_IN_USE);
    }

    public ArtistInUseException(Throwable cause) {
        super(ErrorCode.ARTIST_IN_USE, ErrorCode.ARTIST_IN_USE.getDefaultMessage(), cause);
    }
}
