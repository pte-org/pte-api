package com.pte.assessment.internal.controller;

import com.pte.assessment.dto.response.GenerationPresetResponse;
import com.pte.assessment.internal.constant.GenerationPresetConstants;
import com.pte.assessment.internal.service.GenerationPresetQueryService;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.ResourceServerJwt;
import com.pte.shared.security.SecurityClaims;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The presets endpoint behind the real method-security proxy and exception handler (the repo's controller-test
 * pattern): the four platform author roles read it; learners and tenant roles are refused; no caller is 401.
 */
class GenerationPresetControllerTest {

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private final GenerationPresetQueryService service = mock(GenerationPresetQueryService.class);
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfig.class);
        context.registerBean(GenerationPresetController.class, () -> new GenerationPresetController(service));
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(GenerationPresetController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.listActivePresets()).thenReturn(
                List.of(new GenerationPresetResponse("READ_ALOUD", "SPEAKING", 7)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private static void authenticate(String role, UUID tenant) {
        Jwt.Builder jwt = Jwt.withTokenValue("test-token").header("alg", "RS256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.parse("2026-11-18T00:00:00Z"))
                .expiresAt(Instant.parse("2026-11-19T00:00:00Z")).claim(SecurityClaims.ROLES, List.of(role));
        if (tenant != null) {
            jwt.claim(SecurityClaims.TENANT_ID, tenant.toString());
        }
        SecurityContextHolder.getContext().setAuthentication(ResourceServerJwt.rolesConverter().convert(jwt.build()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "ACADEMIC_MANAGER", "ACADEMIC_STAFF", "PLATFORM_AUTHOR"})
    void platformAuthorsReadThePresets(String role) throws Exception {
        authenticate(role, null);

        mvc.perform(get(GenerationPresetConstants.GENERATION_PRESETS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].taskType").value("READ_ALOUD"))
                .andExpect(jsonPath("$.data[0].section").value("SPEAKING"))
                .andExpect(jsonPath("$.data[0].maxCount").value(7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "HOST_ADMIN", "PROCTOR", "EXAMINER"})
    void learnersAndTenantRolesAreRefused(String role) throws Exception {
        authenticate(role, UUID.randomUUID());

        mvc.perform(get(GenerationPresetConstants.GENERATION_PRESETS_PATH)).andExpect(status().isForbidden());
    }

    /**
     * Without an authentication the method-security proxy refuses before the service is reached. The 401 itself
     * comes from the real filter chain (the path is not public: SecurityConfigGenerationPresetsPathTest).
     */
    @Test
    void anAnonymousCallerNeverReachesTheService() throws Exception {
        mvc.perform(get(GenerationPresetConstants.GENERATION_PRESETS_PATH))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .isNotEqualTo(200));
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).listActivePresets();
    }
}
