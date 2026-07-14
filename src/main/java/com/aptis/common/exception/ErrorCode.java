package com.aptis.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            MessageConstant.BAD_REQUEST_DATA),

    MALFORMED_REQUEST(
            HttpStatus.BAD_REQUEST,
            MessageConstant.BAD_REQUEST_BODY),

    IAM_INVALID_CREDENTIAL(
            HttpStatus.UNAUTHORIZED,
            MessageConstant.INVALID_AUTHORIZED),

    UNAUTHENTICATED(
            HttpStatus.UNAUTHORIZED,
            MessageConstant.UNAUTHORIZED),

    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            MessageConstant.FORBIDDEN),

    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            MessageConstant.NOT_FOUND),

    RESOURCE_CONFLICT(
            HttpStatus.CONFLICT,
            MessageConstant.CONFLICT),

    EXAM_NOT_ASSIGNABLE(
            HttpStatus.BAD_REQUEST,
            MessageConstant.EXAM_NOT_ASSIGNABLE),

    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            MessageConstant.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}