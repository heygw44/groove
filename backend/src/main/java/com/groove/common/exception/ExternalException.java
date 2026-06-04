package com.groove.common.exception;

public class ExternalException extends BusinessException {

    public ExternalException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ExternalException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public ExternalException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
