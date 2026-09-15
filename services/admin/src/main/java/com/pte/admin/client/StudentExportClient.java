package com.pte.admin.client;

import com.pte.admin.client.dto.StudentExportItem;
import com.pte.common.security.InternalServiceAuth;
import com.pte.common.web.ApiResponse;
import com.pte.common.web.ExportPage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Direct, service-key-authenticated client for IAM's rebuild export. */
@Component
public class StudentExportClient {

    private static final int REBUILD_PAGE_SIZE = 200;
    private static final ParameterizedTypeReference<ApiResponse<ExportPage<StudentExportItem>>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String serviceKey;
    private final String bootstrapKey;

    public StudentExportClient(@Value("${iam.base-url}") String baseUrl,
            @Value("${internal.service-key}") String serviceKey,
            @Value("${internal.bootstrap-key}") String bootstrapKey,
            @Value("${iam.export.connect-timeout:5s}") Duration connectTimeout,
            @Value("${iam.export.read-timeout:15s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.serviceKey = serviceKey;
        this.bootstrapKey = bootstrapKey;
    }

    public ExportPage<StudentExportItem> exportStudents(UUID tenantId, String since) {
        ApiResponse<ExportPage<StudentExportItem>> response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/internal/students/export")
                            .queryParam("limit", REBUILD_PAGE_SIZE);
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
                .body(RESPONSE_TYPE);
        return response.data();
    }
}
