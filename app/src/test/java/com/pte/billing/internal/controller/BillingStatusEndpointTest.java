package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.plan.PlanTransitionRequest;
import com.pte.billing.internal.dto.request.application.RejectApplicationRequest;
import com.pte.billing.internal.dto.response.PlanResponse;
import com.pte.billing.internal.dto.response.TenantApplicationResponse;
import com.pte.billing.internal.service.PlanService;
import com.pte.billing.internal.service.TenantApplicationService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.StatusEndpointHarness;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Application review and plan lifecycle use {@code PATCH .../status}; the plan version precondition is kept. */
class BillingStatusEndpointTest {

    private final UUID id = UUID.randomUUID();
    private final CurrentUser operator = StatusEndpointHarness.user("PLATFORM_MANAGER");

    @Test
    void approvingAnApplicationNeedsNoBodyBeyondTheAction() throws Exception {
        TenantApplicationService service = mock(TenantApplicationService.class);
        try (StatusEndpointHarness harness = StatusEndpointHarness
                .of(new TenantApplicationController(service)).as(operator)) {
            harness.patchStatus("/api/v1/applications/{id}/status", "{\"action\":\"APPROVE\"}", id)
                    .andExpect(status().isOk());
        }
        verify(service).approve(id, operator);
    }

    @Test
    void rejectingAnApplicationRequiresAReason() throws Exception {
        TenantApplicationService service = mock(TenantApplicationService.class);
        when(service.reject(any(), any(), any())).thenReturn(mock(TenantApplicationResponse.class));
        try (StatusEndpointHarness harness = StatusEndpointHarness
                .of(new TenantApplicationController(service)).as(operator)) {
            harness.patchStatus("/api/v1/applications/{id}/status", "{\"action\":\"REJECT\"}", id)
                    .andExpect(status().isBadRequest());
            harness.patchStatus("/api/v1/applications/{id}/status",
                    "{\"action\":\"REJECT\",\"reason\":\"incomplete\"}", id).andExpect(status().isOk());
        }
        verify(service).reject(id, new RejectApplicationRequest("incomplete"), operator);
    }

    @Test
    void planActivationAndArchiveCarryTheExpectedVersion() throws Exception {
        PlanService service = mock(PlanService.class);
        PlanResponse response = mock(PlanResponse.class);
        when(service.activate(id, new PlanTransitionRequest(3L), operator)).thenReturn(response);
        when(service.archive(id, new PlanTransitionRequest(4L), operator)).thenReturn(response);
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(new PlanController(service)).as(operator)) {
            harness.patchStatus("/api/v1/plans/{id}/status", "{\"action\":\"ACTIVATE\",\"expectedVersion\":3}", id)
                    .andExpect(status().isOk());
            harness.patchStatus("/api/v1/plans/{id}/status", "{\"action\":\"ARCHIVE\",\"expectedVersion\":4}", id)
                    .andExpect(status().isOk());
            harness.patchStatus("/api/v1/plans/{id}/status", "{\"action\":\"ACTIVATE\"}", id)
                    .andExpect(status().isBadRequest());
        }
        verify(service).activate(id, new PlanTransitionRequest(3L), operator);
        verify(service).archive(id, new PlanTransitionRequest(4L), operator);
    }
}
