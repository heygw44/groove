package com.groove.catalog.label.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 삭제하려는 레이블이 album 에서 참조 중인 경우. 409 응답으로 매핑된다.
 *
 * <p>Album 도입(W5-3) 이후 ON DELETE RESTRICT FK 가 활성화된다. 정책은
 * {@link com.groove.catalog.artist.exception.ArtistInUseException} 과 동일하다.
 */
public class LabelInUseException extends DomainException {

    public LabelInUseException() {
        super(ErrorCode.LABEL_IN_USE);
    }

    public LabelInUseException(Throwable cause) {
        super(ErrorCode.LABEL_IN_USE, ErrorCode.LABEL_IN_USE.getDefaultMessage(), cause);
    }
}
