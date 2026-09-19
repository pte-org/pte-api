package com.pte.billing.internal.controller;

import com.pte.billing.internal.service.PlanService;
import com.pte.billing.internal.dto.response.PlanResponse;
import com.pte.identity.internal.config.SecurityConfig;
import com.pte.shared.web.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanControllerTest {

    @Mock
    private PlanService planService;

    @Mock
    private Authentication authentication;

    private PlanController controller;

    @BeforeEach
    void setUp() {
        controller = new PlanController(planService);
    }

    @Test
    void anonymousRequestReturnsOnlyActiveCatalog() {
        List<PlanResponse> activePlans = List.of(plan("active-plan", "ACTIVE"));
        when(planService.listActive()).thenReturn(activePlans);

        ApiResponse<List<PlanResponse>> response = controller.list(null);

        assertThat(response.data()).isSameAs(activePlans);
        verify(planService).listActive();
        verify(planService, never()).listForAdmin();
    }

    @Test
    void nonPlatformRequestReturnsOnlyActiveCatalog() {
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("ROLE_HOST_ADMIN")))
                .when(authentication).getAuthorities();
        List<PlanResponse> activePlans = List.of(plan("active-plan", "ACTIVE"));
        when(planService.listActive()).thenReturn(activePlans);

        ApiResponse<List<PlanResponse>> response = controller.list(authentication);

        assertThat(response.data()).isSameAs(activePlans);
        verify(planService).listActive();
        verify(planService, never()).listForAdmin();
    }

    @Test
    void platformAdminRequestRetainsFullCatalogAccess() {
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")))
                .when(authentication).getAuthorities();
        List<PlanResponse> allPlans = List.of(
                plan("draft-plan", "DRAFT"),
                plan("active-plan", "ACTIVE"),
                plan("archived-plan", "ARCHIVED"));
        when(planService.listForAdmin()).thenReturn(allPlans);

        ApiResponse<List<PlanResponse>> response = controller.list(authentication);

        assertThat(response.data()).isSameAs(allPlans);
        verify(planService).listForAdmin();
        verify(planService, never()).listActive();
    }

    @Test
    void plansListIsExplicitlyPublicAtTheHttpSecurityBoundary() throws Exception {
        Field publicPaths = SecurityConfig.class.getDeclaredField("PUBLIC_PATHS");
        publicPaths.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> paths = (List<String>) publicPaths.get(null);
        assertThat(paths).contains("/api/v1/plans");
    }

    private PlanResponse plan(String name, String status) {
        return new PlanResponse(
                UUID.randomUUID(), name, null, "STUDENT_CAPACITY", BigDecimal.ZERO,
                "VND", null, null, 10, status);
    }
}
