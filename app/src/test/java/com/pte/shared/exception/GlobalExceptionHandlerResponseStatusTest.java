package com.pte.shared.exception;

import com.pte.shared.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

/** A business rejection thrown as a {@link ResponseStatusException} keeps its status in the API envelope. */
class GlobalExceptionHandlerResponseStatusTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void aConflictKeepsItsStatusAndReason() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "Work is already finalized"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("Work is already finalized");
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void aMissingReasonFallsBackToTheStatusPhrase() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Not Found");
    }

    @Test
    void aServerErrorStatusStaysAnInternalError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).doesNotContain("upstream detail");
    }

    @Test
    void anUnknownRouteIsA404() {
        var response = handler.handleUnknownRoute(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.POST, "api/v1/sessions/x/open", "api/v1/sessions/x/open"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void aWrongVerbIsA405() {
        var response = handler.handleMethodNotAllowed(
                new org.springframework.web.HttpRequestMethodNotSupportedException("POST",
                        java.util.List.of("GET", "PATCH")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow())
                .containsExactlyInAnyOrder(org.springframework.http.HttpMethod.GET, org.springframework.http.HttpMethod.PATCH);
    }

    @Test
    void aBodyWithoutAJsonContentTypeIsA415() {
        var response = handler.handleUnsupportedMediaType(
                new org.springframework.web.HttpMediaTypeNotSupportedException("text/plain not supported"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void aMissingQueryParameterIsA400() {
        var response = handler.handleBadParameter(
                new org.springframework.web.bind.MissingServletRequestParameterException("role", "String"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aBlankStatusReasonIsAValidationError() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> StatusReasonRequiredException.requireReason(" ", "Reason needed"))
                .isInstanceOfSatisfying(StatusReasonRequiredException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(ex.getMessage()).isEqualTo("Reason needed");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }
}
