package com.aptis.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.aptis.common.filter.RequestIdFilter;
import com.aptis.common.response.ApiResponse;
import com.aptis.modules.iam.constant.IamMessageConstants;
import com.aptis.modules.iam.domain.exception.ImportValidationException;
import com.aptis.modules.iam.dto.response.studentimport.ImportValidationErrorResponse;
import com.aptis.modules.iam.dto.response.studentimport.RowError;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(
            ApiException exception,
            HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();

        ApiResponse<Void> response = ApiResponse.error(
                errorCode,
                exception.getMessage(),
                null,
                request.getRequestURI());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(
                    error.getField(),
                    error.getDefaultMessage());
        }

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR,
                fieldErrors,
                request.getRequestURI());

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.MALFORMED_REQUEST,
                null,
                request.getRequestURI());

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    @ExceptionHandler(ImportValidationException.class)
    public ResponseEntity<ApiResponse<ImportValidationErrorResponse>> handleImportValidationException(
            ImportValidationException exception,
            HttpServletRequest request) {
        ImportValidationErrorResponse errorResponse =
                new ImportValidationErrorResponse(exception.getErrors().stream()
                        .map(error -> new RowError(
                                error.rowNumber(),
                                error.errorCode()))
                        .toList());

        ApiResponse<ImportValidationErrorResponse> response = new ApiResponse<>(
                Instant.now(),
                false,
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.VALIDATION_ERROR.name(),
                IamMessageConstants.IMPORT_VALIDATION_FAILED,
                errorResponse,
                null,
                null,
                request.getRequestURI(),
                null);

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        LOGGER.error(
                "Unhandled exception, requestId={}",
                requestId,
                exception);

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.INTERNAL_ERROR,
                null,
                request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
