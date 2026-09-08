package com.pte.scoring.client;

import com.pte.common.web.ApiResponse;
import com.pte.scoring.client.dto.MediaPresignedDownloadResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Resolves a submitted answer's audio/image playback URL for host review
 * (quang-host-answer-review Phase 1) — read-only (GET presign only, scoring
 * never uploads/completes/deletes media). Mirrors exam-delivery's
 * {@code MediaClient} presign-GET pattern exactly; scoring had no dependency
 * on media before this.
 */
@Component
public class MediaClient {

    private static final String CIRCUIT_BREAKER = "media";

    private final RestClient mediaInternalRestClient;

    public MediaClient(RestClient mediaInternalRestClient) {
        this.mediaInternalRestClient = mediaInternalRestClient;
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "presignGetFallback")
    public MediaPresignedDownloadResponse presignGet(UUID mediaPublicId, long ttlSeconds, UUID tenantId) {
        ApiResponse<MediaPresignedDownloadResponse> response = mediaInternalRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/media-objects/{publicId}/presigned-url")
                        .queryParam("ttlSeconds", ttlSeconds)
                        .queryParam("tenantId", tenantId)
                        .build(mediaPublicId))
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<MediaPresignedDownloadResponse>>() {
                });
        return response == null ? null : response.data();
    }

    @SuppressWarnings("unused")
    private MediaPresignedDownloadResponse presignGetFallback(UUID mediaPublicId, long ttlSeconds, UUID tenantId,
                                                                Throwable ex) {
        return null;
    }
}
