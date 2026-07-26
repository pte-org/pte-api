package com.pte.examdelivery.client;

import com.pte.common.web.ApiResponse;
import com.pte.examdelivery.client.dto.SchedulingEntitlementResponse;
import com.pte.examdelivery.domain.exception.NotEntitledException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * The ONE guarded call to scheduling, made only at attempt-create to verify
 * entitlement (enrolled + session open) and fetch composition/snapshot
 * reference (phase-05 design constraint). {@code studentPublicId} is always the
 * CALLER's own identity, already resolved from exam-delivery's own validated
 * JWT — never taken from client input.
 *
 * <p>A clean 403 (not entitled) is a legitimate business outcome, not an
 * infrastructure failure — it's translated to {@link NotEntitledException}
 * and rethrown. {@code application.yml} lists it under this breaker's
 * {@code ignoreExceptions}, so resilience4j propagates it to the caller
 * WITHOUT counting it as a failure or invoking the fallback; only network/5xx
 * failures reach {@code checkEntitlementFallback}.
 */
@Component
public class SchedulingClient {

    private static final String CIRCUIT_BREAKER = "scheduling";

    private final RestClient schedulingRestClient;

    public SchedulingClient(RestClient schedulingRestClient) {
        this.schedulingRestClient = schedulingRestClient;
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "checkEntitlementFallback")
    public SchedulingEntitlementResponse checkEntitlement(UUID sessionPublicId, UUID studentPublicId) {
        try {
            ApiResponse<SchedulingEntitlementResponse> response = schedulingRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/sessions/{publicId}/entitlement")
                            .queryParam("studentPublicId", studentPublicId)
                            .build(sessionPublicId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<SchedulingEntitlementResponse>>() {
                    });
            return response == null ? null : response.data();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new NotEntitledException();
            }
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    private SchedulingEntitlementResponse checkEntitlementFallback(UUID sessionPublicId, UUID studentPublicId, Throwable ex) {
        return null;
    }
}
