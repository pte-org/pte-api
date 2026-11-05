package com.pte.reporting.internal.controller;

import com.pte.reporting.internal.dto.response.ScoreExplanationResponse;
import com.pte.reporting.internal.exception.ReportNotFoundException;
import com.pte.reporting.internal.service.explain.ScoreExplanationService;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.ResourceServerJwt;
import com.pte.shared.security.SecurityClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The explanation endpoint with method security on: only HOST_ADMIN and EXAMINER pass the role guard; Platform Admin,
 * every vendor role, Proctor and Student get 403 before the service runs. The tenant/assignment 404 comes from the
 * service. There is no rubric-version comparison parameter.
 */
class ScoreExplanationControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ATTEMPT = UUID.randomUUID();
    private static final String PATH = "/api/v1/reports/attempts/" + ATTEMPT + "/score-explanation";

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private final ScoreExplanationService service = mock(ScoreExplanationService.class);
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfig.class);
        context.registerBean(ScoreExplanationController.class, () -> new ScoreExplanationController(service));
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ScoreExplanationController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.explain(eq(ATTEMPT), any())).thenReturn(new ScoreExplanationResponse(ATTEMPT, UUID.randomUUID(),
                false, null, null, true, false, UUID.randomUUID(), 1, UUID.randomUUID(), null, List.of(), List.of(),
                List.of(), null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOST_ADMIN", "EXAMINER"})
    void hostAndExaminerReachTheService(String role) throws Exception {
        authenticate(role);

        mvc.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.attemptPublicId").value(ATTEMPT.toString()))
                .andExpect(jsonPath("$.data.traceAvailable").value(true));
        ArgumentCaptor<CurrentUser> caller = ArgumentCaptor.forClass(CurrentUser.class);
        verify(service).explain(eq(ATTEMPT), caller.capture());
        assertThat(caller.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(caller.getValue().hasRole(role)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "ACADEMIC_MANAGER", "ACADEMIC_STAFF", "PLATFORM_AUTHOR",
            "PLATFORM_MANAGER", "PROCTOR", "STUDENT"})
    void everyOtherRoleIsForbiddenBeforeTheServiceRuns(String role) throws Exception {
        authenticate(role);

        mvc.perform(get(PATH)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void aHiddenAttemptIsTheSame404WhateverTheReason() throws Exception {
        authenticate("HOST_ADMIN");
        when(service.explain(eq(ATTEMPT), any())).thenThrow(new ReportNotFoundException());

        mvc.perform(get(PATH)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("REPORT_NOT_FOUND"));
    }

    @Test
    void thereIsNoRubricVersionComparisonParameter() throws Exception {
        authenticate("HOST_ADMIN");

        mvc.perform(get(PATH).param("compareRubricVersion", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        verify(service).explain(eq(ATTEMPT), any());
        Method endpoint = Arrays.stream(ScoreExplanationController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("getScoreExplanation")).findFirst().orElseThrow();
        assertThat(endpoint.getParameters()).hasSize(1);
        assertThat(Arrays.stream(endpoint.getParameters()).map(Parameter::getType)).containsExactly(UUID.class);
        assertThat(Arrays.stream(endpoint.getParameters()).noneMatch(p -> p.isAnnotationPresent(RequestParam.class)))
                .isTrue();
    }

    private static void authenticate(String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.parse("2026-11-05T00:00:00Z")).expiresAt(Instant.parse("2026-11-06T00:00:00Z"))
                .claim(SecurityClaims.TENANT_ID, TENANT.toString())
                .claim(SecurityClaims.ROLES, List.of(roles)).build();
        SecurityContextHolder.getContext().setAuthentication(ResourceServerJwt.rolesConverter().convert(jwt));
    }
}
