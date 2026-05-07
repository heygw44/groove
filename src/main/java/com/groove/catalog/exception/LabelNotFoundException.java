package com.groove.catalog.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 요청한 레이블 ID 가 존재하지 않는 경우. 404 응답으로 매핑된다.
 */
public class LabelNotFoundException extends DomainException {

    public LabelNotFoundException() {
        super(ErrorCode.LABEL_NOT_FOUND);
    }
}
