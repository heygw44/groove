package com.groove.common.exception;

public class DomainException extends BusinessException {

    public DomainException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DomainException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
