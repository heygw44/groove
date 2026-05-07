package com.groove.catalog.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 레이블 이름이 이미 존재하는 경우. 409 응답으로 매핑된다.
 */
public class LabelNameDuplicatedException extends DomainException {

    public LabelNameDuplicatedException() {
        super(ErrorCode.LABEL_NAME_DUPLICATED);
    }

    public LabelNameDuplicatedException(Throwable cause) {
        super(ErrorCode.LABEL_NAME_DUPLICATED, ErrorCode.LABEL_NAME_DUPLICATED.getDefaultMessage(), cause);
    }
}
