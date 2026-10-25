package com.pte.tenancy.internal.controller;

import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.tenancy.TenantSuspensionImpact;
import com.pte.tenancy.internal.dto.request.SuspendTenantRequest;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.service.QuotaGrantService;
import com.pte.tenancy.internal.service.OrganizationLifecycleService;
import com.pte.tenancy.internal.service.OrganizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MVC verifies tenant suspension request and impact response contracts. */
class TenantSuspensionHttpContractTest {

    private final OrganizationLifecycleService lifecycleService = mock(OrganizationLifecycleService.class);
    private final OrganizationService organizationService = mock(OrganizationService.class);
    private final QuotaGrantService quotaGrantService = mock(QuotaGrantService.class);
    private final CurrentUser actor = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    private MockedStatic<CurrentUserContext> context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = org.mockito.Mockito.mockStatic(CurrentUserContext.class);
        context.when(CurrentUserContext::required).thenReturn(actor);
        mvc = MockMvcBuilders.standaloneSetup(new OrganizationController(organizationService, lifecycleService, quotaGrantService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void cleanup() {
        context.close();
    }

    @Test
    void suspendBindsOptionalDaysAndReturnsSuspendedUntil() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Instant suspendedUntil = Instant.parse("2026-10-24T00:00:00Z");
        SuspendTenantRequest request = new SuspendTenantRequest(7, "impact-digest");
        when(lifecycleService.suspendWithConfirmation(tenantId, request, actor)).thenReturn(new OrganizationResponse(
                tenantId, "TENANT", "Tenant", "SCHOOL", "TAX", "SUSPENDED", suspendedUntil,
                "starter", 100, null, null, null, null, null, null, null, "MAIN"));

        mvc.perform(patch("/api/v1/organizations/{publicId}/status", tenantId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"action\":\"SUSPEND\",\"days\":7,\"impactDigest\":\"impact-digest\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.suspendedUntil").value("2026-10-24T00:00:00Z"));

        verify(lifecycleService).suspendWithConfirmation(tenantId, request, actor);
    }

    @Test
    void impactBindsOptionalDaysAndReturnsSubscriptionLossSummary() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Instant suspendedUntil = Instant.parse("2026-10-24T00:00:00Z");
        UUID subscriptionId = UUID.randomUUID();
        when(lifecycleService.suspensionImpact(tenantId, 7)).thenReturn(new TenantSuspensionImpact(
                tenantId, 7, suspendedUntil, 2, 7,
                List.of(new TenantSuspensionImpact.SubscriptionImpact(
                        subscriptionId, UUID.randomUUID(), suspendedUntil, 7))));

        mvc.perform(get("/api/v1/organizations/{publicId}/suspension-impact", tenantId).param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspensionDays").value(7))
                .andExpect(jsonPath("$.data.activeSubscriptionCount").value(2))
                .andExpect(jsonPath("$.data.totalDaysLost").value(7))
                .andExpect(jsonPath("$.data.expiringSubscriptions[0].subscriptionPublicId")
                        .value(subscriptionId.toString()));

        verify(lifecycleService).suspensionImpact(tenantId, 7);
    }
}
