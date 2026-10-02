package com.pte.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard response envelope for every endpoint.
 *
 * <p>The legacy {@code success}, {@code data}, and {@code message} fields stay
 * stable. {@code code} and {@code userMessage} are optional metadata for
 * clients that need to separate branching from display copy.</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        @JsonInclude(JsonInclude.Include.NON_NULL) String userMessage) {

    /** Keeps source compatibility with the original three-field envelope. */
    public ApiResponse(boolean success, T data, String message) {
        this(success, data, message, null, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }

    /** Creates an error with separate machine-readable and display metadata. */
    public static <T> ApiResponse<T> error(String code, String message, String userMessage) {
        return new ApiResponse<>(false, null, message, code, userMessage);
    }
}
