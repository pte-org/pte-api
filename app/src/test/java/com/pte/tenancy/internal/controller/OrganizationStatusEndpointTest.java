package com.pte.tenancy.internal.controller;

import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.StatusEndpointHarness;
import com.pte.tenancy.internal.dto.request.SuspendTenantRequest;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.service.OrganizationLifecycleService;
import com.pte.tenancy.internal.service.OrganizationService;
import com.pte.tenancy.internal.service.QuotaGrantService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reactivation needs no confirmation; suspension still requires the digest of the impact preview. */
class OrganizationStatusEndpointTest {

    private final UUID id = UUID.randomUUID();
    private final CurrentUser operator = StatusEndpointHarness.user("PLATFORM_ADMIN");
    private final OrganizationLifecycleService lifecycle = mock(OrganizationLifecycleService.class);
    private final OrganizationController controller = new OrganizationController(mock(OrganizationService.class),
            lifecycle, mock(QuotaGrantService.class));

    @Test
    void reactivateReachesTheLifecycleServiceWithoutADigest() throws Exception {
        when(lifecycle.reactivate(id, operator)).thenReturn(mock(OrganizationResponse.class));
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(controller).as(operator)) {
            harness.patchStatus("/api/v1/organizations/{id}/status", "{\"action\":\"REACTIVATE\"}", id)
                    .andExpect(status().isOk());
        }
        verify(lifecycle).reactivate(id, operator);
    }

    @Test
    void suspendWithoutTheImpactDigestIsRejectedBeforeAnyChange() throws Exception {
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(controller).as(operator)) {
            harness.patchStatus("/api/v1/organizations/{id}/status", "{\"action\":\"SUSPEND\",\"days\":7}", id)
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(lifecycle);
    }

    @Test
    void suspendWithTheDigestIsConfirmedByTheService() throws Exception {
        when(lifecycle.suspendWithConfirmation(id, new SuspendTenantRequest(3, "digest"), operator))
                .thenReturn(mock(OrganizationResponse.class));
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(controller).as(operator)) {
            harness.patchStatus("/api/v1/organizations/{id}/status",
                    "{\"action\":\"SUSPEND\",\"days\":3,\"impactDigest\":\"digest\"}", id)
                    .andExpect(status().isOk());
        }
        verify(lifecycle).suspendWithConfirmation(id, new SuspendTenantRequest(3, "digest"), operator);
    }
}
