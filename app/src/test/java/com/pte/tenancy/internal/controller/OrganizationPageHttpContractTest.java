package com.pte.tenancy.internal.controller;

import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.ResourceServerJwt;
import com.pte.shared.security.SecurityClaims;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import com.pte.tenancy.internal.dto.request.OrganizationPageRequest;
import com.pte.tenancy.internal.service.OrganizationLifecycleService;
import com.pte.tenancy.internal.service.OrganizationService;
import com.pte.tenancy.internal.service.QuotaGrantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses the real method-security proxy and MVC binding/validation, without running the application database. */
class OrganizationPageHttpContractTest {

    private final OrganizationService service = mock(OrganizationService.class);
    private final UUID userId = UUID.randomUUID();
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfig.class);
        context.registerBean(OrganizationController.class, () -> new OrganizationController(service,
                mock(OrganizationLifecycleService.class), mock(QuotaGrantService.class)));
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(OrganizationController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_MANAGER"})
    void defaultsToTwentyRowsAndReturnsTheCommonPageMetadata(String role) throws Exception {
        authenticate(role);
        when(service.page(any(), any())).thenReturn(new PagedResult<>(List.of(),
                new PageMeta(0, 20, 100000, 5000, true, false, true, false)));

        mvc.perform(get("/api/v1/organizations/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.data.meta.page").value(0))
                .andExpect(jsonPath("$.data.meta.size").value(20))
                .andExpect(jsonPath("$.data.meta.totalElements").value(100000))
                .andExpect(jsonPath("$.data.meta.totalPages").value(5000));

        verify(service).page(new CurrentUser(userId, null, List.of(role)),
                new OrganizationPageRequest(0, 20, null));
    }

    @Test
    void bindsSearchAndPageIndependentlyFromTheExistingUnpagedList() throws Exception {
        authenticate("PLATFORM_ADMIN");
        when(service.page(any(), any())).thenReturn(new PagedResult<>(List.of(),
                new PageMeta(2, 20, 45, 3, false, true, false, true)));

        mvc.perform(get("/api/v1/organizations/page").param("page", "2").param("size", "20")
                        .param("q", "school"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.page").value(2));

        verify(service).page(new CurrentUser(userId, null, List.of("PLATFORM_ADMIN")),
                new OrganizationPageRequest(2, 20, "school"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOST_ADMIN", "STUDENT", "ACADEMIC_MANAGER"})
    void tenantAndAcademicCallersCannotReadThePlatformPage(String role) throws Exception {
        authenticate(role);

        mvc.perform(get("/api/v1/organizations/page"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101"})
    void invalidPageSizesReturnBadRequestBeforeQuerying(String size) throws Exception {
        authenticate("PLATFORM_ADMIN");

        mvc.perform(get("/api/v1/organizations/page").param("size", size))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void negativePageReturnsBadRequestBeforeQuerying() throws Exception {
        authenticate("PLATFORM_ADMIN");

        mvc.perform(get("/api/v1/organizations/page").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private void authenticate(String role) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject(userId.toString())
                .claim(SecurityClaims.ROLES, List.of(role)).build();
        SecurityContextHolder.getContext().setAuthentication(ResourceServerJwt.rolesConverter().convert(jwt));
    }
}
