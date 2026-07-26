package com.pte.examdelivery.client;

import com.pte.common.web.ApiResponse;
import com.pte.examdelivery.client.dto.AuthoringSnapshotContentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * The other half of the ONE guarded attempt-create pull: full snapshot content
 * (with correct answers) from authoring's {@code /internal/**} surface.
 * Never called during a live attempt.
 */
@Component
public class AuthoringClient {

    private static final String CIRCUIT_BREAKER = "authoring";

    private final RestClient authoringInternalRestClient;

    public AuthoringClient(RestClient authoringInternalRestClient) {
        this.authoringInternalRestClient = authoringInternalRestClient;
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "fetchContentFallback")
    public AuthoringSnapshotContentResponse fetchContent(UUID snapshotPublicId) {
        ApiResponse<AuthoringSnapshotContentResponse> response = authoringInternalRestClient.get()
                .uri("/internal/snapshots/{publicId}", snapshotPublicId)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<AuthoringSnapshotContentResponse>>() {
                });
        return response == null ? null : response.data();
    }

    @SuppressWarnings("unused")
    private AuthoringSnapshotContentResponse fetchContentFallback(UUID snapshotPublicId, Throwable ex) {
        return null;
    }
}
