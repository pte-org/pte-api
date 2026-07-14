package com.aptis.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.aptis.common.filter.RequestIdFilter;
import com.aptis.common.response.ApiResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void apiExceptionUsesTheSharedErrorEnvelope() {
        MDC.put(RequestIdFilter.MDC_KEY, "request-456");
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/admins/99");

        ResponseEntity<ApiResponse<Void>> result = handler.handleApiException(
                new ApiException(ErrorCode.RESOURCE_NOT_FOUND),
                request);

        assertThat(result.getStatusCode().value()).isEqualTo(404);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().success()).isFalse();
        assertThat(result.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(result.getBody().path()).isEqualTo("/api/v1/admins/99");
        assertThat(result.getBody().requestId()).isEqualTo("request-456");
    }
}
