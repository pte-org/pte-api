package com.pte.proctor.client;

import com.pte.common.web.ApiResponse;
import com.pte.proctor.client.dto.SchedulingProctorAssignmentResponse;
import com.pte.proctor.domain.exception.NotAssignedToSessionException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * The ONE guarded call proctor makes — to scheduling, once per {@code ProctorSession}
 * open (not per command), to verify the caller is actually assigned to the
 * session. Never calls exam-delivery synchronously (ADR-002: proctor's only
 * edge to exam-delivery is the async {@code ProctorCommand} event).
 */
@Component
public class SchedulingClient {

    private static final String CIRCUIT_BREAKER = "scheduling";

    private final RestClient schedulingRestClient;

    public SchedulingClient(RestClient schedulingRestClient) {
        this.schedulingRestClient = schedulingRestClient;
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "checkAssignmentFallback")
    public SchedulingProctorAssignmentResponse checkAssignment(UUID sessionPublicId, UUID proctorPublicId) {
        try {
            ApiResponse<SchedulingProctorAssignmentResponse> response = schedulingRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/sessions/{publicId}/proctor-assignment")
                            .queryParam("proctorPublicId", proctorPublicId)
                            .build(sessionPublicId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<SchedulingProctorAssignmentResponse>>() {
                    });
            return response == null ? null : response.data();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new NotAssignedToSessionException();
            }
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    private SchedulingProctorAssignmentResponse checkAssignmentFallback(UUID sessionPublicId, UUID proctorPublicId, Throwable ex) {
        return null;
    }
}
