package com.pte.scheduling.client;

import com.pte.common.web.ApiResponse;
import com.pte.scheduling.client.dto.AuthoringSnapshotResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * The ONE allowed synchronous outbound call in scheduling — a guarded pull of
 * snapshot composition metadata from authoring, made only at session-creation
 * time (phase-04 design constraint; mirrors the exam-delivery precedent).
 * Circuit-broken so a slow/down authoring degrades to a fast failure, not a
 * hung host request. Replaced by the {@code ExamSnapshotPublished} consumer in
 * Phase 6.
 */
@Component
public class AuthoringClient {

    private static final String CIRCUIT_BREAKER = "authoring";

    private final RestClient authoringRestClient;

    public AuthoringClient(RestClient authoringRestClient) {
        this.authoringRestClient = authoringRestClient;
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "fetchSnapshotFallback")
    public AuthoringSnapshotResponse fetchSnapshot(UUID snapshotPublicId) {
        ApiResponse<AuthoringSnapshotResponse> response = authoringRestClient.get()
                .uri("/internal/snapshots/{publicId}/summary", snapshotPublicId)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<AuthoringSnapshotResponse>>() {
                });
        return response == null ? null : response.data();
    }

    @SuppressWarnings("unused")
    private AuthoringSnapshotResponse fetchSnapshotFallback(UUID snapshotPublicId, Throwable ex) {
        return null;
    }
}
