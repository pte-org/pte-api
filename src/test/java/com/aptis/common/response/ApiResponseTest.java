package com.aptis.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import com.aptis.common.exception.ErrorCode;
import com.aptis.common.filter.RequestIdFilter;

class ApiResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void successContainsDataAndRequestContext() {
        MDC.put(RequestIdFilter.MDC_KEY, "request-123");

        ApiResponse<String> response = ApiResponse.success(
                HttpStatus.CREATED,
                "ADMIN_CREATED",
                "Admin created successfully",
                "admin-data",
                "/api/v1/admins");

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.code()).isEqualTo("ADMIN_CREATED");
        assertThat(response.data()).isEqualTo("admin-data");
        assertThat(response.meta()).isNull();
        assertThat(response.errors()).isNull();
        assertThat(response.path()).isEqualTo("/api/v1/admins");
        assertThat(response.requestId()).isEqualTo("request-123");
    }

    @Test
    void pagedMovesPageInformationToMeta() {
        PageImpl<String> page = new PageImpl<>(
                List.of("third", "fourth"),
                PageRequest.of(1, 2),
                5);

        ApiResponse<List<String>> response = ApiResponse.paged(
                page,
                "/api/v1/questions");

        assertThat(response.data()).containsExactly("third", "fourth");
        assertThat(response.meta()).isInstanceOf(PageMeta.class);

        PageMeta meta = (PageMeta) response.meta();
        assertThat(meta.page()).isEqualTo(1);
        assertThat(meta.size()).isEqualTo(2);
        assertThat(meta.totalElements()).isEqualTo(5);
        assertThat(meta.totalPages()).isEqualTo(3);
        assertThat(meta.first()).isFalse();
        assertThat(meta.last()).isFalse();
        assertThat(meta.hasNext()).isTrue();
        assertThat(meta.hasPrevious()).isTrue();
    }

    @Test
    void errorContainsStableCodeAndFieldErrors() {
        Map<String, String> errors = Map.of("email", "must not be blank");

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR,
                errors,
                "/api/v1/admin/login");

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.data()).isNull();
        assertThat(response.errors()).containsEntry("email", "must not be blank");
    }
}
