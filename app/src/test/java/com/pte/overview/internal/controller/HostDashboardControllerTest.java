package com.pte.overview.internal.controller;

import com.pte.overview.internal.HostDashboardFixtures;
import com.pte.overview.internal.cache.DashboardCache;
import com.pte.overview.internal.service.HostDashboardService;
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
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static com.pte.overview.internal.HostDashboardFixtures.OTHER_TENANT;
import static com.pte.overview.internal.HostDashboardFixtures.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc against the real controller wrapped in the real method-security proxy
 * ({@code @PreAuthorize}), with a real {@code JwtAuthenticationToken} built the same
 * way the resource-server filter builds it, so the role check and
 * {@code CurrentUserContext} both run for real. Services below the controller use
 * the fixture facades; the 401 for a missing token belongs to the filter chain.
 */
class HostDashboardControllerTest {

    private static final String PATH = "/api/v1/dashboard/host";
    private static final UUID USER = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private HostDashboardFixtures fx;
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        fx = new HostDashboardFixtures().stubHappyPath(TENANT);
        mvc = buildMvc(HostDashboardFixtures.passThroughCache());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.close();
        }
    }

    private MockMvc buildMvc(DashboardCache cache) {
        if (context != null) {
            context.close();
        }
        HostDashboardService service = fx.service(cache);
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfig.class);
        context.registerBean(HostDashboardController.class, () -> new HostDashboardController(service));
        context.refresh();
        return MockMvcBuilders.standaloneSetup(context.getBean(HostDashboardController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static void authenticate(UUID tenantId, String... roles) {
        Jwt.Builder jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject(USER.toString())
                .issuedAt(HostDashboardFixtures.NOW.minusSeconds(60))
                .expiresAt(HostDashboardFixtures.NOW.plusSeconds(600))
                .claim(SecurityClaims.ROLES, List.of(roles));
        if (tenantId != null) {
            jwt.claim(SecurityClaims.TENANT_ID, tenantId.toString());
        }
        SecurityContextHolder.getContext().setAuthentication(ResourceServerJwt.rolesConverter().convert(jwt.build()));
    }

    private void assertNoFacadeWasTouched() {
        verifyNoInteractions(fx.identity, fx.enrollment, fx.sessions, fx.attempts, fx.tenancy);
    }

    // ---- 200 ----

    @Test
    void hostAdminGetsTheFullEnvelopeWithEverySectionOk() throws Exception {
        authenticate(TENANT, "HOST_ADMIN");

        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meta.windowDays").value(30))
                .andExpect(jsonPath("$.data.meta.zone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.data.learners.status").value("OK"))
                .andExpect(jsonPath("$.data.learners.data.total").value(100))
                .andExpect(jsonPath("$.data.learners.data.newInWindow.changePct").value(50.0))
                .andExpect(jsonPath("$.data.learners.data.activeLearners.changePct").doesNotExist())
                .andExpect(jsonPath("$.data.quota.status").value("OK"))
                .andExpect(jsonPath("$.data.quota.data.remaining").value(400))
                .andExpect(jsonPath("$.data.academics.status").value("OK"))
                .andExpect(jsonPath("$.data.staff.status").value("OK"))
                .andExpect(jsonPath("$.data.exams.status").value("OK"))
                .andExpect(jsonPath("$.data.exams.data.upcoming.length()").value(1))
                .andExpect(jsonPath("$.data.attempts.status").value("OK"))
                .andExpect(jsonPath("$.data.attempts.data.monthlySubmitted.length()").value(12))
                .andExpect(jsonPath("$.data.attempts.data.monthlySubmitted[0].month").value("2025-11"))
                .andExpect(jsonPath("$.data.attempts.data.monthlySubmitted[11].month").value("2026-10"))
                .andExpect(jsonPath("$.data.attempts.data.monthlySubmitted[11].partial").value(true))
                .andExpect(jsonPath("$.data.attempts.data.monthlySubmitted[0].partial").value(false));
    }

    @Test
    void hostAdminGetsAllElevenSectionsOkFromTheNamedTenantAFixture() throws Exception {
        fx.stubOperations(TENANT);
        authenticate(TENANT, "HOST_ADMIN");

        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.learners.status").value("OK"))
                .andExpect(jsonPath("$.data.quota.status").value("OK"))
                .andExpect(jsonPath("$.data.academics.status").value("OK"))
                .andExpect(jsonPath("$.data.staff.status").value("OK"))
                .andExpect(jsonPath("$.data.exams.status").value("OK"))
                .andExpect(jsonPath("$.data.attempts.status").value("OK"))
                .andExpect(jsonPath("$.data.grading.status").value("OK"))
                .andExpect(jsonPath("$.data.subscription.status").value("OK"))
                .andExpect(jsonPath("$.data.billing.status").value("OK"))
                .andExpect(jsonPath("$.data.support.status").value("OK"))
                .andExpect(jsonPath("$.data.recentActivity.status").value("OK"))
                .andExpect(jsonPath("$.data.grading.data.pendingAnswers").value(5))
                .andExpect(jsonPath("$.data.grading.data.aiScoringAnswers").value(2))
                .andExpect(jsonPath("$.data.grading.data.failedAnswers").value(1))
                .andExpect(jsonPath("$.data.grading.data.closedSessionsAwaitingFinalisation").value(3))
                .andExpect(jsonPath("$.data.grading.data.reportsAwaitingPublication").value(4))
                .andExpect(jsonPath("$.data.subscription.data.current.length()").value(1))
                .andExpect(jsonPath("$.data.subscription.data.current[0].planName").value("Tenant A Exam Pack"))
                .andExpect(jsonPath("$.data.subscription.data.daysToExpiry").value(12))
                .andExpect(jsonPath("$.data.subscription.data.expiringSoon").value(true))
                .andExpect(jsonPath("$.data.billing.data.pendingOrders").value(2))
                .andExpect(jsonPath("$.data.support.data.open").value(3))
                .andExpect(jsonPath("$.data.support.data.inProgress").value(2))
                .andExpect(jsonPath("$.data.support.data.resolvedAwaitingClose").value(1))
                .andExpect(jsonPath("$.data.recentActivity.data.length()").value(2))
                .andExpect(jsonPath("$.data.recentActivity.data[0].action").value("CLASS_CREATED"))
                .andExpect(jsonPath("$.data.recentActivity.data[0].summary").doesNotExist())
                .andExpect(jsonPath("$.data.billing.data.paymentLink").doesNotExist())
                .andExpect(jsonPath("$.data.billing.data.paymentLinkUrl").doesNotExist());
    }

    @Test
    void tenantBDataNeverAppearsInTenantAsResponseEvenWhenTenantBIsFullyStubbed() throws Exception {
        fx.stubOperations(TENANT);
        fx.stubFullPath(OTHER_TENANT).stubOperationsOfTenantB(OTHER_TENANT);
        authenticate(TENANT, "HOST_ADMIN");

        String body = mvc.perform(get(PATH).param("tenantId", OTHER_TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grading.data.pendingAnswers").value(5))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("TENANT-B-SECRET").doesNotContain("70707").doesNotContain("80808").doesNotContain("90909").doesNotContain("60606").doesNotContain("50505").doesNotContain("40404").doesNotContain("30303").doesNotContain("20202").doesNotContain("10101").doesNotContain("TENANT_B_ACTION").doesNotContain("Tenant B");
        verify(fx.scoring).backlog(TENANT);
        verify(fx.scoring, never()).backlog(OTHER_TENANT);
        verify(fx.reporting, never()).countAwaitingPublication(OTHER_TENANT);
        verify(fx.billing, never()).subscriptionSummary(OTHER_TENANT);
        verify(fx.billing, never()).pendingOrderCount(OTHER_TENANT);
        verify(fx.support, never()).ticketCounts(OTHER_TENANT);
        verify(fx.audit, never()).recent(org.mockito.ArgumentMatchers.eq(OTHER_TENANT),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void aFailingScoringSectionStill200sWithGradingInErrorAndTheRestOk() throws Exception {
        fx.stubOperations(TENANT);
        when(fx.scoring.backlog(TENANT)).thenThrow(new IllegalStateException("jdbc:postgresql://secret-host"));
        authenticate(TENANT, "HOST_ADMIN");

        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grading.status").value("ERROR"))
                .andExpect(jsonPath("$.data.grading.data").doesNotExist())
                .andExpect(jsonPath("$.data.subscription.status").value("OK"))
                .andExpect(jsonPath("$.data.billing.status").value("OK"))
                .andExpect(jsonPath("$.data.support.status").value("OK"))
                .andExpect(jsonPath("$.data.recentActivity.status").value("OK"))
                .andExpect(jsonPath("$.data.attempts.status").value("OK"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-host"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7", "30", "90"})
    void supportedWindowsAreAccepted(String days) throws Exception {
        authenticate(TENANT, "HOST_ADMIN");

        mvc.perform(get(PATH).param("windowDays", days))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.windowDays").value(Integer.parseInt(days)));
    }

    @Test
    void blankWindowFallsBackToTheDefault() throws Exception {
        authenticate(TENANT, "HOST_ADMIN");

        mvc.perform(get(PATH).param("windowDays", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.windowDays").value(30));
    }

    @Test
    void tokenTenantIsTheOnlyTenantEverUsedEvenIfTheCallerPassesAnother() throws Exception {
        authenticate(TENANT, "HOST_ADMIN");

        mvc.perform(get(PATH).param("tenantId", OTHER_TENANT.toString()).param("tenant", OTHER_TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.learners.data.total").value(100));

        verify(fx.identity).studentStatusCounts(TENANT);
        verify(fx.identity, never()).studentStatusCounts(OTHER_TENANT);
        verify(fx.attempts, never()).countInProgress(OTHER_TENANT);
    }

    @Test
    void controllerAcceptsNoTenantParameter() throws Exception {
        Method handler = HostDashboardController.class.getMethod("host", String.class);

        assertThat(handler.getParameterCount()).isEqualTo(1);
        assertThat(handler.getParameters()[0].getAnnotation(RequestParam.class).name()).isEqualTo("windowDays");
        assertThat(HostDashboardController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('HOST_ADMIN')");
        assertThat(HostDashboardController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/dashboard");
    }

    // ---- 400 ----

    @ParameterizedTest
    @ValueSource(strings = {"15", "abc", "0", "-7", "7.5", "365", "99999999999"})
    void invalidWindowIs400WithTheStableErrorCodeAndNoDataFetched(String days) throws Exception {
        authenticate(TENANT, "HOST_ADMIN");

        mvc.perform(get(PATH).param("windowDays", days))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_WINDOW"));

        assertNoFacadeWasTouched();
    }

    // ---- 403 ----

    @Test
    void platformAdminWithoutATenantIsForbidden() throws Exception {
        authenticate(null, "PLATFORM_ADMIN");

        mvc.perform(get(PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertNoFacadeWasTouched();
    }

    @Test
    void platformUserCarryingATenantClaimIsStillForbiddenWithoutTheHostRole() throws Exception {
        authenticate(TENANT, "PLATFORM_MANAGER");

        mvc.perform(get(PATH)).andExpect(status().isForbidden());

        assertNoFacadeWasTouched();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "PROCTOR", "EXAMINER", "ACADEMIC_STAFF", "ACADEMIC_MANAGER"})
    void everyNonHostRoleIsForbiddenEvenWithATenant(String role) throws Exception {
        authenticate(TENANT, role);

        mvc.perform(get(PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertNoFacadeWasTouched();
    }

    @Test
    void hostAdminTokenWithoutATenantIsForbiddenNotServed() throws Exception {
        authenticate(null, "HOST_ADMIN");

        mvc.perform(get(PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertNoFacadeWasTouched();
    }

    @Test
    void forbiddenWinsOverAnInvalidWindowSoTheValidationDoesNotLeakToUnauthorisedCallers() throws Exception {
        authenticate(TENANT, "STUDENT");

        mvc.perform(get(PATH).param("windowDays", "abc")).andExpect(status().isForbidden());
    }

    // ---- section isolation / cache fallback over HTTP ----

    @Test
    void aFailingSectionStillReturns200WithThatSectionInErrorAndNoRawMessage() throws Exception {
        authenticate(TENANT, "HOST_ADMIN");
        when(fx.attempts.countInProgress(TENANT)).thenThrow(new IllegalStateException("jdbc:postgresql://secret-host"));

        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.attempts.status").value("ERROR"))
                .andExpect(jsonPath("$.data.attempts.data").doesNotExist())
                .andExpect(jsonPath("$.data.learners.status").value("OK"))
                .andExpect(jsonPath("$.data.quota.status").value("OK"))
                .andExpect(jsonPath("$.data.academics.status").value("OK"))
                .andExpect(jsonPath("$.data.staff.status").value("OK"))
                .andExpect(jsonPath("$.data.exams.status").value("OK"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-host"))));
    }

    @Test
    void redisBeingDownDoesNotFailTheRequest() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("connection refused"));
        MockMvc withRealCache = buildMvc(new DashboardCache(redis, JsonMapper.builder().findAndAddModules().build()));
        authenticate(TENANT, "HOST_ADMIN");

        withRealCache.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempts.status").value("OK"))
                .andExpect(jsonPath("$.data.attempts.data.monthlySubmitted.length()").value(12));
    }
}
