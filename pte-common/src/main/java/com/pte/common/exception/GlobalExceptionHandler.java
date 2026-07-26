package com.pte.common.exception;

import com.pte.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Shared base handler mapping domain + validation errors to {@link ApiResponse}.
 * Each service exposes it by declaring a {@code @RestControllerAdvice} subclass
 * (empty) so component scanning picks it up within that service only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_FALLBACK = "VALIDATION_ERROR";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : VALIDATION_FALLBACK;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(INTERNAL_ERROR));
    }
}
