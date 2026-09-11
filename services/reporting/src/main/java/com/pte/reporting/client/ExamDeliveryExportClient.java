package com.pte.reporting.client;

import com.pte.common.security.InternalServiceAuth;
import com.pte.common.web.ApiResponse;
import com.pte.common.web.ExportPage;
import com.pte.reporting.messaging.consumer.dto.AnswerSubmittedEvent;
import com.pte.reporting.messaging.consumer.dto.AttemptSubmittedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * Client for exam-delivery's rebuild export endpoints (rabbitmq-outbox-migration
 * Phase 9). Reuses reporting's own {@code AttemptSubmittedEvent}/{@code
 * AnswerSubmittedEvent} consumer DTOs as the deserialization target — their
 * existing {@code @JsonIgnoreProperties(ignoreUnknown = true)} plus matching
 * field names already make them valid export-item shapes, and pagination
 * only needs {@link ExportPage#nextCursor()}, never a per-item timestamp — so
 * no separate export-specific DTO is needed on this side.
 */
@Component
public class ExamDeliveryExportClient {

    private static final ParameterizedTypeReference<ApiResponse<ExportPage<AttemptSubmittedEvent>>> ATTEMPTS_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ApiResponse<ExportPage<AnswerSubmittedEvent>>> ANSWERS_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String serviceKey;
    private final String bootstrapKey;

    public ExamDeliveryExportClient(@Value("${exam-delivery.base-url}") String baseUrl,
            @Value("${internal.service-key}") String serviceKey,
            @Value("${internal.bootstrap-key}") String bootstrapKey) {
        this.restClient = RestClient.create(baseUrl);
        this.serviceKey = serviceKey;
        this.bootstrapKey = bootstrapKey;
    }

    public ExportPage<AttemptSubmittedEvent> exportAttempts(UUID tenantId, String since) {
        return get("/internal/attempts/export", tenantId, since, ATTEMPTS_TYPE);
    }

    public ExportPage<AnswerSubmittedEvent> exportAnswers(UUID tenantId, String since) {
        return get("/internal/answers/export", tenantId, since, ANSWERS_TYPE);
    }

    private <T> ExportPage<T> get(String path, UUID tenantId, String since,
            ParameterizedTypeReference<ApiResponse<ExportPage<T>>> responseType) {
        ApiResponse<ExportPage<T>> response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(path);
                    Optional.ofNullable(tenantId).ifPresent(id -> uriBuilder.queryParam("tenantId", id));
                    Optional.ofNullable(since).ifPresent(cursor -> uriBuilder.queryParam("since", cursor));
                    return uriBuilder.build();
                })
                .header(InternalServiceAuth.HEADER, serviceKey)
                .headers(headers -> {
                    // Bootstrap (all-tenant) mode: caller passes tenantId = null and must be
                    // pre-authorized to do so — send the bootstrap key too, or the target
                    // service correctly rejects the null-tenantId request.
                    if (tenantId == null) {
                        headers.set(InternalServiceAuth.BOOTSTRAP_HEADER, bootstrapKey);
                    }
                })
                .retrieve()
                .body(responseType);
        return response.data();
    }
}
