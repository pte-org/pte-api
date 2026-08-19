package com.pte.reporting.client;

import com.pte.common.security.InternalServiceAuth;
import com.pte.common.web.ApiResponse;
import com.pte.common.web.ExportPage;
import com.pte.reporting.messaging.consumer.dto.AnswerScoredEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * Client for scoring's rebuild export endpoint (rabbitmq-outbox-migration
 * Phase 9). Reuses reporting's own {@code AnswerScoredEvent} consumer DTO as
 * the deserialization target — see {@link ExamDeliveryExportClient} for the
 * same rationale.
 */
@Component
public class ScoringExportClient {

    private static final ParameterizedTypeReference<ApiResponse<ExportPage<AnswerScoredEvent>>> ANSWERS_SCORED_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String serviceKey;
    private final String bootstrapKey;

    public ScoringExportClient(@Value("${scoring.base-url}") String baseUrl,
            @Value("${internal.service-key}") String serviceKey,
            @Value("${internal.bootstrap-key}") String bootstrapKey) {
        this.restClient = RestClient.create(baseUrl);
        this.serviceKey = serviceKey;
        this.bootstrapKey = bootstrapKey;
    }

    public ExportPage<AnswerScoredEvent> exportAnswersScored(UUID tenantId, String since) {
        ApiResponse<ExportPage<AnswerScoredEvent>> response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/internal/answers-scored/export");
                    Optional.ofNullable(tenantId).ifPresent(id -> uriBuilder.queryParam("tenantId", id));
                    Optional.ofNullable(since).ifPresent(cursor -> uriBuilder.queryParam("since", cursor));
                    return uriBuilder.build();
                })
                .header(InternalServiceAuth.HEADER, serviceKey)
                .headers(headers -> {
                    if (tenantId == null) {
                        headers.set(InternalServiceAuth.BOOTSTRAP_HEADER, bootstrapKey);
                    }
                })
                .retrieve()
                .body(ANSWERS_SCORED_TYPE);
        return response.data();
    }
}
