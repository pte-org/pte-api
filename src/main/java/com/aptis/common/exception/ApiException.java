package com.aptis.common.exception;

public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;

    public ApiException(ErrorCode errCode) {
        super(errCode.getDefaultMessage());
        this.errorCode = errCode;
    }

    public ApiException(ErrorCode errCode, String message) {
        super(message);
        this.errorCode = errCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
