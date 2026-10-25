package com.pte.overview.internal.controller;

import com.pte.overview.internal.AdminDashboardFixtures;
import com.pte.overview.internal.AdminDashboardFixtures.FakeRedis;
import com.pte.overview.internal.HostDashboardFixtures;
import com.pte.overview.internal.cache.DashboardCache;
import com.pte.overview.internal.dto.response.AdminSectionData;
import com.pte.overview.internal.service.admin.AdminDashboardService;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.pte.overview.internal.AdminDashboardFixtures.TENANT_ID;
import static com.pte.overview.internal.AdminDashboardFixtures.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc against the real admin controller wrapped in the real method-security proxy with a real
 * {@code JwtAuthenticationToken} (same wiring as the host controller test). The services below the controller use
 * the fixture facades and a real cache over an in-memory Redis where noted.
 */
class AdminDashboardControllerTest {

    private static final String PATH = "/api/v1/dashboard/admin";
    /** The nine sections an academic caller must never read (everything except the question bank). */
    private static final List<String> OPERATIONS_SECTIONS = List.of("tenants", "learners", "recentTenants",
            "applications", "licenses", "subscriptions", "plans", "support", "revenue");
    /** Phase 4: ten sections in all. */
    private static final List<String> ALL_SECTIONS = List.of("tenants", "learners", "recentTenants",
            "applications", "licenses", "subscriptions", "plans", "support", "revenue", "questionBank");
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private AdminDashboardFixtures fx;
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        fx = new AdminDashboardFixtures().stubHappyPath();
        mvc = buildMvc(AdminDashboardFixtures.passThroughCache());
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
        AdminDashboardService service = fx.service(cache);
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfig.class);
        context.registerBean(AdminDashboardController.class, () -> new AdminDashboardController(service));
        context.refresh();
        return MockMvcBuilders.standaloneSetup(context.getBean(AdminDashboardController.class))
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
        verifyNoInteractions(fx.tenancy, fx.tenancyOverview, fx.revenueFacade, fx.itembank, fx.identity, fx.attempts,
                fx.billing, fx.support);
    }

    /** Everything except the item bank facade: the only one an academic caller's request may read. */
    private void assertNoOperationsFacadeWasTouched() {
        verifyNoInteractions(fx.tenancy, fx.tenancyOverview, fx.revenueFacade, fx.identity, fx.attempts, fx.billing,
                fx.support);
    }

    // ---- 200: platform admin and manager ----

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_MANAGER"})
    void operationsRolesGetEverySectionOk(String role) throws Exception {
        authenticate(null, role);

        var result = mvc.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meta.windowDays").value(30))
                .andExpect(jsonPath("$.data.meta.zone").value("Asia/Ho_Chi_Minh"));
        for (String section : ALL_SECTIONS) {
            result.andExpect(jsonPath("$.data." + section + ".status").value("OK"));
            result.andExpect(jsonPath("$.data." + section + ".data").exists());
        }
    }

    @Test
    void thePlatformAdminResponseCarriesTheFrozenFieldNamesOfEverySection() throws Exception {
        authenticate(null, "PLATFORM_ADMIN");

        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenants.data.total").value(20))
                .andExpect(jsonPath("$.data.tenants.data.active").value(18))
                .andExpect(jsonPath("$.data.tenants.data.suspended").value(2))
                .andExpect(jsonPath("$.data.tenants.data.byOrganizationType.SCHOOL").value(12))
                .andExpect(jsonPath("$.data.tenants.data.newInWindow.current").value(6))
                .andExpect(jsonPath("$.data.tenants.data.newInWindow.previous").value(4))
                .andExpect(jsonPath("$.data.tenants.data.newInWindow.changePct").value(50.0))
                .andExpect(jsonPath("$.data.tenants.data.monthlyCreated.length()").value(12))
                .andExpect(jsonPath("$.data.tenants.data.monthlyCreated[0].month").value("2025-11"))
                .andExpect(jsonPath("$.data.tenants.data.monthlyCreated[11].month").value("2026-10"))
                .andExpect(jsonPath("$.data.tenants.data.monthlyCreated[11].partial").value(true))
                .andExpect(jsonPath("$.data.learners.data.totalStudents").value(156))
                .andExpect(jsonPath("$.data.learners.data.totalSeats").value(760))
                .andExpect(jsonPath("$.data.learners.data.activeLearners.current").value(300))
                .andExpect(jsonPath("$.data.learners.data.topUtilisation.length()").value(5))
                .andExpect(jsonPath("$.data.learners.data.topUtilisation[0].code").value("T4"))
                .andExpect(jsonPath("$.data.learners.data.topUtilisation[0].utilisationPct").value(100.0))
                .andExpect(jsonPath("$.data.recentTenants.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.recentTenants.data.items[0].planName").value("Pro Pack"))
                .andExpect(jsonPath("$.data.recentTenants.data.items[0].studentCount").value(90))
                .andExpect(jsonPath("$.data.recentTenants.data.items[0].seatLimit").value(100))
                .andExpect(jsonPath("$.data.recentTenants.data.items[1].planName").doesNotExist())
                .andExpect(jsonPath("$.data.applications.data.pending").value(7))
                .andExpect(jsonPath("$.data.applications.data.oldestPending.length()").value(2))
                .andExpect(jsonPath("$.data.applications.data.oldestPending[0].orgName").value("Org One"))
                .andExpect(jsonPath("$.data.licenses.data.issuedValid").value(40))
                .andExpect(jsonPath("$.data.licenses.data.expired").value(9))
                .andExpect(jsonPath("$.data.licenses.data.expiringSoonDays").value(7))
                .andExpect(jsonPath("$.data.subscriptions.data.active").value(33))
                .andExpect(jsonPath("$.data.subscriptions.data.expiringSoonDays").value(30))
                .andExpect(jsonPath("$.data.subscriptions.data.activatedBySource.PAYMENT").value(4))
                .andExpect(jsonPath("$.data.plans.data.active").value(4))
                .andExpect(jsonPath("$.data.plans.data.byType.EXAM_PACKAGE").value(5))
                .andExpect(jsonPath("$.data.support.data.open").value(11))
                .andExpect(jsonPath("$.data.support.data.inProgress").value(4))
                .andExpect(jsonPath("$.data.support.data.oldestOpen.length()").value(2))
                .andExpect(jsonPath("$.data.support.data.oldestOpen[0].category").value("BUG"))
                // Phase 4: revenue (per currency, exact amounts) and question bank
                .andExpect(jsonPath("$.data.revenue.status").value("OK"))
                .andExpect(jsonPath("$.data.revenue.data.source").value("ONLINE_ORDERS"))
                .andExpect(jsonPath("$.data.revenue.data.paidOrders.current").value(5))
                .andExpect(jsonPath("$.data.revenue.data.paidOrders.previous").value(3))
                .andExpect(jsonPath("$.data.revenue.data.currencies.length()").value(3))
                .andExpect(jsonPath("$.data.revenue.data.currencies[0].currency").value("EUR"))
                .andExpect(jsonPath("$.data.revenue.data.currencies[1].currency").value("USD"))
                .andExpect(jsonPath("$.data.revenue.data.currencies[2].currency").value("VND"))
                .andExpect(jsonPath("$.data.revenue.data.currencies[2].current").value(1500000.5))
                .andExpect(jsonPath("$.data.revenue.data.currencies[2].previous").value(1000000.0))
                .andExpect(jsonPath("$.data.revenue.data.currencies[2].changePct").value(50.0))
                .andExpect(jsonPath("$.data.revenue.data.currencies[2].paidOrders.current").value(3))
                .andExpect(jsonPath("$.data.revenue.data.currencies[2].monthly.length()").value(12))
                .andExpect(jsonPath("$.data.revenue.data.currencies[2].monthly[0].month").value("2025-11"))
                .andExpect(jsonPath("$.data.revenue.data.currencies[2].monthly[11].month").value("2026-10"))
                .andExpect(jsonPath("$.data.revenue.data.currencies[2].monthly[11].partial").value(true))
                .andExpect(jsonPath("$.data.revenue.data.currencies[2].monthly[10].partial").value(false))
                .andExpect(jsonPath("$.data.revenue.data.currencies[1].changePct").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.questionBank.status").value("OK"))
                .andExpect(jsonPath("$.data.questionBank.data.total").value(120))
                .andExpect(jsonPath("$.data.questionBank.data.byStatus.DRAFT").value(20))
                .andExpect(jsonPath("$.data.questionBank.data.byStatus.APPROVED").value(80))
                .andExpect(jsonPath("$.data.questionBank.data.bySection.READING").value(30))
                .andExpect(jsonPath("$.data.questionBank.data.byPool.PRACTICE").value(20))
                .andExpect(jsonPath("$.data.questionBank.data.approvedByPool.EXAM").value(70))
                .andExpect(jsonPath("$.data.questionBank.data.createdInWindow.current").value(12))
                .andExpect(jsonPath("$.data.questionBank.data.createdInWindow.previous").value(8))
                .andExpect(jsonPath("$.data.questionBank.data.createdInWindow.changePct").value(50.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7", "30", "90"})
    void supportedWindowsAreAccepted(String days) throws Exception {
        authenticate(null, "PLATFORM_ADMIN");

        mvc.perform(get(PATH).param("windowDays", days))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.windowDays").value(Integer.parseInt(days)));
    }

    @Test
    void blankWindowFallsBackToTheDefault() throws Exception {
        authenticate(null, "PLATFORM_ADMIN");

        mvc.perform(get(PATH).param("windowDays", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.windowDays").value(30));
    }

    @Test
    void anyTenantParameterIsIgnoredBecauseThePlatformDashboardIsNotTenantScoped() throws Exception {
        authenticate(null, "PLATFORM_ADMIN");

        mvc.perform(get(PATH).param("tenantId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenants.data.total").value(20));
    }

    // ---- 200 with FORBIDDEN: academic roles ----

    @ParameterizedTest
    @ValueSource(strings = {"ACADEMIC_STAFF", "ACADEMIC_MANAGER", "PLATFORM_AUTHOR"})
    void academicRolesAndTheLegacyAuthorAliasGet200WithQuestionBankOkAndEveryOperationsSectionForbidden(String role)
            throws Exception {
        authenticate(null, role);

        // Phase 4 contract change: these callers used to get all 8 sections FORBIDDEN; the question bank is now OK.
        var result = mvc.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meta.windowDays").value(30));
        for (String section : OPERATIONS_SECTIONS) {
            result.andExpect(jsonPath("$.data." + section + ".status").value("FORBIDDEN"));
            result.andExpect(jsonPath("$.data." + section + ".data").value(org.hamcrest.Matchers.nullValue()));
        }
        result.andExpect(jsonPath("$.data.revenue.status").value("FORBIDDEN"));
        result.andExpect(jsonPath("$.data.questionBank.status").value("OK"));
        result.andExpect(jsonPath("$.data.questionBank.data.total").value(120));
        result.andExpect(jsonPath("$.data.questionBank.data.approvedByPool.EXAM").value(70));
        assertNoOperationsFacadeWasTouched();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACADEMIC_STAFF", "ACADEMIC_MANAGER", "PLATFORM_AUTHOR"})
    void anAcademicResponseBodyNeverContainsRevenueOrAnyOperationsData(String role) throws Exception {
        authenticate(null, role);

        String body = mvc.perform(get(PATH)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("ONLINE_ORDERS").doesNotContain("1500000").doesNotContain("VND")
                .doesNotContain("Alpha School").doesNotContain("Pro Pack").doesNotContain("Org One");
    }

    @Test
    void anAcademicCallerNeverSeesDataAnAdminJustCachedForTheSameWindow() throws Exception {
        FakeRedis redis = new FakeRedis();
        MockMvc cached = buildMvc(redis.cache());
        authenticate(null, "PLATFORM_ADMIN");
        cached.perform(get(PATH).param("windowDays", "30")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenants.data.total").value(20))
                .andExpect(jsonPath("$.data.revenue.data.currencies[0].currency").value("EUR"));
        assertThat(redis.store).hasSize(10);
        SecurityContextHolder.clearContext();
        authenticate(null, "ACADEMIC_STAFF");

        String body = cached.perform(get(PATH).param("windowDays", "30")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenants.status").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data.revenue.status").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data.questionBank.status").value("OK"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Alpha School").doesNotContain("Pro Pack").doesNotContain("Org One")
                .doesNotContain("ONLINE_ORDERS").doesNotContain("VND");
        // the academic caller got an entry of its own (11 = 10 operations-bucket entries + 1 academic question bank)
        assertThat(redis.store).hasSize(11);
    }

    // ---- 403 ----

    @Test
    void hostAdminWithATenantIdIsForbiddenAndNothingIsRead() throws Exception {
        authenticate(TENANT_ID, "HOST_ADMIN");

        mvc.perform(get(PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertNoFacadeWasTouched();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_MANAGER", "ACADEMIC_MANAGER", "ACADEMIC_STAFF",
            "PLATFORM_AUTHOR"})
    void aPlatformRoleThatCarriesATenantIdIsRejectedByTheControllerItself(String role) throws Exception {
        authenticate(TENANT_ID, role);

        mvc.perform(get(PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertNoFacadeWasTouched();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOST_ADMIN", "PROCTOR", "EXAMINER", "STUDENT"})
    void everyTenantRoleIsForbiddenWithOrWithoutATenantClaim(String role) throws Exception {
        authenticate(TENANT_ID, role);
        mvc.perform(get(PATH)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        SecurityContextHolder.clearContext();
        authenticate(null, role);
        mvc.perform(get(PATH)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertNoFacadeWasTouched();
    }

    @Test
    void aTokenWithNoRolesIsForbidden() throws Exception {
        authenticate(null);

        mvc.perform(get(PATH)).andExpect(status().isForbidden());

        assertNoFacadeWasTouched();
    }

    @Test
    void anAnonymousRequestIsStoppedByMethodSecurityBeforeTheServiceRuns() throws Exception {
        SecurityContextHolder.clearContext();

        // There is no filter chain in this standalone setup (its 401 is covered by the next test), so the
        // method-security proxy is the only gate: it must reject the call before the controller body runs.
        int httpStatus = mvc.perform(get(PATH)).andReturn().getResponse().getStatus();
        String body = mvc.perform(get(PATH)).andReturn().getResponse().getContentAsString();

        assertThat(httpStatus).isGreaterThanOrEqualTo(400);
        assertThat(body).doesNotContain("\"tenants\"").doesNotContain("Alpha School");
        assertNoFacadeWasTouched();
    }

    @Test
    void forbiddenWinsOverAnInvalidWindowSoValidationDoesNotLeakToUnauthorisedCallers() throws Exception {
        authenticate(TENANT_ID, "HOST_ADMIN");

        mvc.perform(get(PATH).param("windowDays", "abc")).andExpect(status().isForbidden());
    }

    // ---- 400 ----

    @ParameterizedTest
    @ValueSource(strings = {"15", "abc", "0", "-7", "7.5", "365", "99999999999"})
    void anInvalidWindowIs400WithTheStableErrorCodeAndNoDataFetched(String days) throws Exception {
        authenticate(null, "PLATFORM_ADMIN");

        mvc.perform(get(PATH).param("windowDays", days))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_WINDOW"));

        assertNoFacadeWasTouched();
    }

    // ---- section failures over HTTP ----

    @Test
    void aFailingSectionStill200sWithThatSectionInErrorAndNoRawMessage() throws Exception {
        authenticate(null, "PLATFORM_ADMIN");
        org.mockito.Mockito.when(fx.billing.licenseCounts(HostDashboardFixtures.NOW))
                .thenThrow(new IllegalStateException("jdbc:postgresql://secret-host"));

        String body = mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.licenses.status").value("ERROR"))
                .andExpect(jsonPath("$.data.licenses.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.tenants.status").value("OK"))
                .andExpect(jsonPath("$.data.subscriptions.status").value("OK"))
                .andExpect(jsonPath("$.data.support.status").value("OK"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret-host").doesNotContain("jdbc");
    }

    @Test
    void aFailingRevenueOrQuestionBankSectionStill200sWithOnlyThatSectionInErrorAndNoRawMessage() throws Exception {
        authenticate(null, "PLATFORM_ADMIN");
        org.mockito.Mockito.when(fx.revenueFacade.paidRevenueBetween(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("jdbc:postgresql://orders-host"));
        org.mockito.Mockito.when(fx.itembank.counts()).thenThrow(new IllegalStateException("bank-host timeout"));

        String body = mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revenue.status").value("ERROR"))
                .andExpect(jsonPath("$.data.revenue.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.questionBank.status").value("ERROR"))
                .andExpect(jsonPath("$.data.questionBank.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.tenants.status").value("OK"))
                .andExpect(jsonPath("$.data.subscriptions.status").value("OK"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("orders-host").doesNotContain("bank-host").doesNotContain("jdbc");
    }

    @Test
    void redisBeingDownDoesNotFailTheRequest() throws Exception {
        FakeRedis redis = new FakeRedis();
        org.mockito.Mockito.doThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"))
                .when(redis.ops).get(org.mockito.ArgumentMatchers.anyString());
        MockMvc withRealCache = buildMvc(redis.cache());
        authenticate(null, "PLATFORM_ADMIN");

        withRealCache.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenants.status").value("OK"))
                .andExpect(jsonPath("$.data.support.status").value("OK"));
    }

    // ---- contract of the controller ----

    @Test
    void controllerAcceptsOnlyTheWindowParameterAndIsGuardedByThePlatformRoles() throws Exception {
        Method handler = AdminDashboardController.class.getMethod("admin", String.class);

        assertThat(handler.getParameterCount()).isEqualTo(1);
        assertThat(handler.getParameters()[0].getAnnotation(RequestParam.class).name()).isEqualTo("windowDays");
        assertThat(AdminDashboardController.class.getAnnotation(PreAuthorize.class).value())
                .contains("PLATFORM_ADMIN").contains("PLATFORM_MANAGER").contains("ACADEMIC_MANAGER")
                .contains("ACADEMIC_STAFF").contains("PLATFORM_AUTHOR")
                .doesNotContain("HOST_ADMIN").doesNotContain("STUDENT");
        assertThat(AdminDashboardController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/dashboard");
    }

    // ---- no secrets in the response ----

    private static final Set<String> FORBIDDEN_KEY_FRAGMENTS = Set.of("licensecode", "licensekey", "codevalue",
            "contactemail", "contactphone", "email", "phone", "taxcode", "description", "submitter", "password",
            "secret", "token", "paymentlink", "checkout", "providerreference", "revokereason", "rejectreason",
            "reviewedby", "note", "message", "stacktrace", "exception");

    private static void collectKeys(JsonNode node, String path, List<String> keys) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                keys.add(entry.getKey());
                collectKeys(entry.getValue(), path + "." + entry.getKey(), keys);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectKeys(child, path + "[]", keys));
        }
    }

    @Test
    void theFullResponseNeverCarriesLicenseCodesSecretsContactEmailsTaxCodesOrTicketDescriptions() throws Exception {
        authenticate(null, "PLATFORM_ADMIN");
        String body = mvc.perform(get(PATH).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> keys = new ArrayList<>();
        // only the data subtree: the envelope itself legitimately has a (null) "message" key
        collectKeys(JSON.readTree(body).get("data"), "$", keys);

        assertThat(keys).isNotEmpty().contains("tenants", "licenses", "oldestOpen", "oldestPending");
        for (String key : keys) {
            String lower = key.toLowerCase(Locale.ROOT);
            FORBIDDEN_KEY_FRAGMENTS.forEach(fragment -> assertThat(lower).as("response key '%s'", key)
                    .doesNotContain(fragment));
        }
    }

    /** Payment-provider, link and customer vocabulary that must never appear under the revenue section. */
    private static final Set<String> REVENUE_FORBIDDEN_FRAGMENTS = Set.of("payment", "link", "url", "provider",
            "checkout", "customer", "tenant", "buyer", "ordercode", "payos", "licensecode", "plan");

    @Test
    void theRevenueSectionCarriesOnlyAmountsCountsAndMonthsNeverPaymentLinkProviderCustomerOrTenantData()
            throws Exception {
        authenticate(null, "PLATFORM_ADMIN");
        String body = mvc.perform(get(PATH).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> keys = new ArrayList<>();
        collectKeys(JSON.readTree(body).get("data").get("revenue"), "$", keys);

        // exactly the documented vocabulary: nothing else may be added to the section without a test change
        assertThat(new HashSet<>(keys)).containsExactlyInAnyOrder("status", "data", "source", "paidOrders", "current",
                "previous", "changePct", "currencies", "currency", "monthly", "month", "amount", "partial");
        for (String key : keys) {
            String lower = key.toLowerCase(Locale.ROOT);
            REVENUE_FORBIDDEN_FRAGMENTS.forEach(fragment -> assertThat(lower).as("revenue key '%s'", key)
                    .doesNotContain(fragment));
            FORBIDDEN_KEY_FRAGMENTS.forEach(fragment -> assertThat(lower).as("revenue key '%s'", key)
                    .doesNotContain(fragment));
        }
    }

    @Test
    void theQuestionBankSectionCarriesOnlyCountsNeverQuestionTextAnswersOrAuthors() throws Exception {
        authenticate(null, "ACADEMIC_MANAGER");
        String body = mvc.perform(get(PATH).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> keys = new ArrayList<>();
        collectKeys(JSON.readTree(body).get("data").get("questionBank"), "$", keys);

        assertThat(new HashSet<>(keys)).containsExactlyInAnyOrder("status", "data", "total", "byStatus", "DRAFT",
                "PENDING_APPROVAL", "APPROVED", "ARCHIVED", "bySection", "LISTENING", "SPEAKING", "READING", "WRITING",
                "byPool", "EXAM", "PRACTICE", "approvedByPool", "createdInWindow", "current", "previous", "changePct");
    }

    @Test
    void theFacadeRowsOfRevenueAndQuestionBankOnlyHaveSafeFields() {
        Set<String> revenue = new HashSet<>();
        Arrays.stream(com.pte.billing.BillingRevenueOverviewService.CurrencyPaidTotal.class.getRecordComponents())
                .forEach(c -> revenue.add(c.getName()));
        Set<String> bank = new HashSet<>();
        Arrays.stream(com.pte.itembank.ItembankOverviewService.QuestionBankCounts.class.getRecordComponents())
                .forEach(c -> bank.add(c.getName()));

        assertThat(revenue).containsExactlyInAnyOrder("currency", "amount", "paidOrders");
        assertThat(bank).containsExactlyInAnyOrder("total", "byStatus", "bySection", "byPool", "approvedByPool");
    }

    @Test
    void noPayloadRecordDeclaresAFieldThatCouldHoldASecret() {
        Set<String> names = new HashSet<>();
        for (Class<?> type : AdminSectionData.class.getDeclaredClasses()) {
            if (type.isRecord()) {
                Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName)
                        .map(name -> name.toLowerCase(Locale.ROOT)).forEach(names::add);
            }
        }

        assertThat(names).isNotEmpty();
        for (String name : names) {
            FORBIDDEN_KEY_FRAGMENTS.forEach(fragment -> assertThat(name).as("payload field '%s'", name)
                    .doesNotContain(fragment));
        }
    }

    @Test
    void theFacadeRowsThatFeedTheResponseOnlyHaveSafeFields() {
        Set<String> support = new HashSet<>();
        Arrays.stream(com.pte.support.SupportOverviewService.OpenTicketBrief.class.getRecordComponents())
                .forEach(c -> support.add(c.getName()));
        Set<String> applications = new HashSet<>();
        Arrays.stream(com.pte.billing.BillingOverviewService.PendingApplicationView.class.getRecordComponents())
                .forEach(c -> applications.add(c.getName()));

        assertThat(support).containsExactlyInAnyOrder("publicId", "tenantId", "category", "openedAt");
        assertThat(applications).containsExactlyInAnyOrder("publicId", "orgName", "orgType", "requestedCode",
                "receivedAt");
    }
}
