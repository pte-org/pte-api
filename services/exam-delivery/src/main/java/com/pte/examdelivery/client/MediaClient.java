package com.pte.examdelivery.client;

import com.pte.common.web.ApiResponse;
import com.pte.examdelivery.client.dto.MediaPresignedDownloadResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Resolves a listening item's audio URL — part of the ONE guarded attempt-create
 * pull (StartAttempt only). Never called during a live attempt; the resolved
 * URL is pinned onto {@code PinnedItem} and reused for the attempt's lifetime.
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
