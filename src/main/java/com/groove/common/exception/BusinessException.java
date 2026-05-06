package com.groove.common.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public abstract class BusinessException extends ErrorResponseException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.getStatus());
        this.errorCode = errorCode;
        getBody().setDetail(errorCode.getDefaultMessage());
        getBody().setProperty("code", errorCode.getCode());
    }

    protected BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getStatus());
        this.errorCode = errorCode;
        getBody().setDetail(detail);
        getBody().setProperty("code", errorCode.getCode());
    }

    protected BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getStatus(), ProblemDetail.forStatusAndDetail(errorCode.getStatus(), detail), cause);
        this.errorCode = errorCode;
        getBody().setProperty("code", errorCode.getCode());
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
