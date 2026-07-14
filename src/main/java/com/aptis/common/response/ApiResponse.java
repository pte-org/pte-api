package com.aptis.common.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

import com.aptis.common.exception.ErrorCode;
import com.aptis.common.filter.RequestIdFilter;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        Instant timestamp,
        boolean success,
        int status,
        String code,
        String message,
        T data,
        Object meta,
        Map<String, String> errors,
        String path,
        String requestId) {

    public static <T> ApiResponse<T> success(
            HttpStatus status,
            String code,
            String message,
            T data,
            String path) {
        return new ApiResponse<>(
                Instant.now(),
                true,
                status.value(),
                code,
                message,
                data,
                null,
                null,
                path,
                currentRequestId());
    }

    public static <T> ApiResponse<T> success(T data, String path) {
        return success(
                HttpStatus.OK,
                "SUCCESS",
                "Request completed successfully",
                data,
                path);
    }

    public static <T> ApiResponse<List<T>> paged(
            HttpStatus status,
            String code,
            String message,
            Page<T> page,
            String path) {
        return new ApiResponse<>(
                Instant.now(),
                true,
                status.value(),
                code,
                message,
                page.getContent(),
                PageMeta.from(page),
                null,
                path,
                currentRequestId());
    }

    public static <T> ApiResponse<List<T>> paged(Page<T> page, String path) {
        return paged(
                HttpStatus.OK,
                "SUCCESS",
                "Request completed successfully",
                page,
                path);
    }

    public static ApiResponse<Void> error(
            ErrorCode errorCode,
            Map<String, String> errors,
            String path) {
        return error(errorCode, errorCode.getDefaultMessage(), errors, path);
    }

    public static ApiResponse<Void> error(
            ErrorCode errorCode,
            String message,
            Map<String, String> errors,
            String path) {
        return new ApiResponse<>(
                Instant.now(),
                false,
                errorCode.getStatus().value(),
                errorCode.name(),
                message,
                null,
                null,
                errors,
                path,
                currentRequestId());
    }

    private static String currentRequestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
