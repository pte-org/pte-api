package com.pte.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard response envelope for every endpoint across all services.
 * Shape frozen as a cross-service contract: {@code { success, data, message }}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(boolean success, T data, String message) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
