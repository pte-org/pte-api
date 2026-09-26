package com.pte.shared.exception;

import com.pte.shared.constant.SharedConstants;
import com.pte.shared.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain + validation errors to {@link ApiResponse} for the whole app.
 * Unlike the per-service microservice era (each service needed its own empty
 * subclass so component-scan picked this up within that service only), the
 * monolith has one component scan for everything, so this class is registered
 * directly — no per-module subclass needed.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * {@code ex.getData()} is null for almost every {@link DomainException}
     * (an {@code ApiResponse} with a null {@code data} field, same as before) —
     * only a subtype that opted into the 3-arg constructor (e.g. {@code
     * InsufficientQuestionBankException}'s shortage list) carries a non-null
     * structured payload. Keeping this generic (rather than one handler per
     * exception type) avoids importing any module's {@code internal}
     * exception classes into {@code shared}, which {@code ModuleStructureTest}
     * would otherwise flag as a boundary violation.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Object>> handleDomain(DomainException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiResponse<>(false, ex.getData(), ex.getMessage(), ex.getCode(), ex.getUserMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : SharedConstants.VALIDATION_FALLBACK;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(SharedConstants.VALIDATION_FALLBACK, message, message));
    }

    /**
     * The request body failed to parse into the target DTO — malformed JSON, wrong type,
     * or (the common case) a number field that overflows its Java type (e.g. a 12-digit
     * value into an {@code int}). Jackson wraps that as an unchecked exception during
     * {@code @RequestBody} binding, which otherwise falls through to the 500 handler
     * below even though the fault is entirely in the client's payload.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(SharedConstants.MALFORMED_REQUEST,
                        SharedConstants.MALFORMED_REQUEST_MESSAGE, SharedConstants.MALFORMED_REQUEST_MESSAGE));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(SharedConstants.ACCESS_DENIED, SharedConstants.ACCESS_DENIED,
                        SharedConstants.ACCESS_DENIED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(SharedConstants.INTERNAL_ERROR, SharedConstants.INTERNAL_ERROR, null));
    }
}
