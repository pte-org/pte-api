package com.pte.billing.internal.service;

import com.pte.billing.domain.*;
import com.pte.billing.domain.enums.*;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.repository.*;
import com.pte.billing.internal.exception.LicenseCodeException;
import com.pte.billing.internal.dto.request.PlanTransitionRequest;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.config.ClockConfig;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Independent committed transactions on a dedicated local PostgreSQL database. */
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "logging.level.root=ERROR",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.springframework=ERROR"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({LicenseCodeService.class, LicenseCodePersistenceService.class, LicenseCodeGenerator.class,
        SubscriptionActivationService.class, SubscriptionPersistenceService.class, LicenseKeyGenerator.class,
        ClockConfig.class, PlanService.class, AuditLogService.class})
@EnabledIfSystemProperty(named = "lifecycle.test.db.url", matches = ".+")
@Timeout(120)
class LicenseIssuePostgresIntegrationTest {
    private static final String URL = "jdbc:postgresql://127.0.0.1:55439/lifecycle_test?currentSchema=migration_clean";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        if (!URL.equals(System.getProperty("lifecycle.test.db.url"))) throw new IllegalArgumentException("Dedicated test DB required");
        Flyway.configure().dataSource(URL, "codex_lifecycle_test", "").schemas("migration_clean")
                .locations("classpath:db/migration").load().migrate();
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> "codex_lifecycle_test");
        registry.add("spring.datasource.password", () -> "");
    }
    @Autowired LicenseCodeService service;
    @Autowired LicenseCodePersistenceService persistence;
    @Autowired LicenseCodeRepository codes;
    @Autowired PlanRepository plans;
    @Autowired PlanService planService;
    @Autowired SubscriptionRepository subscriptions;
    @MockitoSpyBean LicenseIssueIntentRepository intents;
    @MockitoSpyBean LicenseCodeGenerator generator;
    @MockitoSpyBean SubscriptionRepository subscriptionSpy;
    @MockitoBean TenancyService tenancy;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    private final CurrentUser admin = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    private <T> T tx(Supplier<T> action) { return new TransactionTemplate(transactions).execute(status -> action.get()); }
    private Plan plan(PlanType type) {
        return tx(() -> {
            Plan plan = new Plan(); plan.setName("Phase04 fixture"); plan.setType(type);
            plan.setPrice(BigDecimal.TEN); plan.setCurrency("VND"); plan.setStatus(PlanStatus.ACTIVE);
            if (type == PlanType.EXAM_PACKAGE) { plan.setDurationDays(30); plan.setMaxStudentsPerSession(20); }
            else plan.setExtraStudentSlots(10);
            return plans.saveAndFlush(plan);
        });
    }
    private List<Throwable> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable action : List.of(first, second)) futures.add(pool.submit(() -> {
                ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                try { action.run(); return null; } catch (RuntimeException ex) { return ex; }
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return Arrays.asList(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        }
    }

    @Test void tenSameIntentRacesCommitOneCodeAndReplayOriginalIdentity() throws Exception {
        Plan plan = plan(PlanType.EXAM_PACKAGE);
        for (int i = 0; i < 10; i++) {
            UUID key = UUID.randomUUID();
            List<LicenseIssueReceipt> results = Collections.synchronizedList(new ArrayList<>());
            Runnable issue = () -> results.add(service.issue(plan.getPublicId(), null, key, admin));
            assertThat(race(issue, issue)).containsOnlyNulls();
            assertThat(results).hasSize(2);
            assertThat(results.get(0).publicId()).isEqualTo(results.get(1).publicId());
            LicenseIssueIntent intent = tx(() -> intents.findByActorPublicIdAndOperationAndIdempotencyKey(
                    admin.userId(), "ISSUE_LICENSE_CODE", key).orElseThrow());
            assertThat(tx(() -> codes.findByPublicId(intent.getLicenseCodePublicId()))).isPresent();
            assertThat(service.issue(plan.getPublicId(), null, key, admin).publicId()).isEqualTo(intent.getLicenseCodePublicId());
        }
        assertThat(tx(() -> codes.findAll().stream().filter(c -> c.getPlanId().equals(plan.getPublicId())).count())).isEqualTo(10);
    }

    @Test void tenDifferentPayloadRacesRejectLoserWithoutOrphanCode() throws Exception {
        for (int i = 0; i < 10; i++) {
            Plan one = plan(PlanType.EXAM_PACKAGE), two = plan(PlanType.EXAM_PACKAGE);
            UUID key = UUID.randomUUID();
            List<Throwable> result = race(() -> service.issue(one.getPublicId(), null, key, admin),
                    () -> service.issue(two.getPublicId(), null, key, admin));
            assertThat(result.stream().filter(Objects::nonNull).toList()).singleElement().isInstanceOf(LicenseCodeException.class);
            assertThat(tx(() -> codes.findAll().stream().filter(c -> Set.of(one.getPublicId(), two.getPublicId()).contains(c.getPlanId())).count())).isEqualTo(1);
        }
    }

    @Test void intentFailureAfterCodeInsertRollsBackBothRows() {
        Plan plan = plan(PlanType.EXAM_PACKAGE); UUID key = UUID.randomUUID();
        doThrow(new org.springframework.dao.DataIntegrityViolationException("injected intent failure"))
                .when(intents).saveAndFlush(any(LicenseIssueIntent.class));
        try {
            assertThatThrownBy(() -> service.issue(plan.getPublicId(), null, key, admin)).isInstanceOf(LicenseCodeException.class);
            assertThat(tx(() -> codes.findAll().stream().filter(c -> c.getPlanId().equals(plan.getPublicId())).count())).isZero();
            assertThat(tx(() -> intents.findByActorPublicIdAndOperationAndIdempotencyKey(admin.userId(), "ISSUE_LICENSE_CODE", key))).isEmpty();
        } finally { reset(intents); }
    }

    @Test void replaySurvivesExpiryAndPlanArchive() {
        Plan plan = plan(PlanType.EXAM_PACKAGE); UUID key = UUID.randomUUID();
        LicenseIssueReceipt issued = service.issue(plan.getPublicId(), null, key, admin);
        tx(() -> { LicenseCode code = codes.findByPublicId(issued.publicId()).orElseThrow(); code.expire(); return codes.saveAndFlush(code); });
        planService.archive(plan.getPublicId(), new PlanTransitionRequest(plan.getVersion()));
        LicenseIssueReceipt replay = service.issue(plan.getPublicId(), null, key, admin);
        assertThat(replay.publicId()).isEqualTo(issued.publicId()); assertThat(replay.status()).isEqualTo("EXPIRED");
        assertThat(replay.replayed()).isTrue();
    }

    @Test void capacityNewIssueIsBlockedButLegacyCodeStillGrantsQuota() {
        Plan plan = plan(PlanType.STUDENT_CAPACITY);
        assertThatThrownBy(() -> service.issue(plan.getPublicId(), null, UUID.randomUUID(), admin)).isInstanceOf(LicenseCodeException.class);
        UUID tenant = UUID.randomUUID(), grant = UUID.randomUUID();
        when(tenancy.grantQuota(eq(tenant), eq(10), anyString())).thenReturn(grant);
        LicenseCode legacy = tx(() -> codes.saveAndFlush(LicenseCode.issue("LEGACY-" + UUID.randomUUID().toString().substring(0, 20).toUpperCase(Locale.ROOT),
                plan.getPublicId(), admin.userId(), Instant.now(), null)));
        assertThat(service.redeem(legacy.getCode(), new CurrentUser(UUID.randomUUID(), tenant, List.of("HOST_ADMIN"))).targetPublicId()).isEqualTo(grant);
        assertThat(tx(() -> codes.findByPublicId(legacy.getPublicId()).orElseThrow().getStatus())).isEqualTo(LicenseCodeStatus.REDEEMED);
        verify(tenancy).grantQuota(eq(tenant), eq(10), anyString());
    }

    @Test void tenTwoTenantRedemptionsHaveOneCommittedSubscription() throws Exception {
        Plan plan = plan(PlanType.EXAM_PACKAGE);
        for (int i = 0; i < 10; i++) {
            LicenseIssueReceipt receipt = service.issue(plan.getPublicId(), null, UUID.randomUUID(), admin);
            LicenseCode code = tx(() -> codes.findByPublicId(receipt.publicId()).orElseThrow());
            CurrentUser one = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
            CurrentUser two = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
            assertThat(race(() -> service.redeem(code.getCode(), one), () -> service.redeem(code.getCode(), two))
                    .stream().filter(Objects::nonNull).count()).isEqualTo(1);
            LicenseCode redeemed = tx(() -> codes.findByPublicId(code.getPublicId()).orElseThrow());
            assertThat(redeemed.getStatus()).isEqualTo(LicenseCodeStatus.REDEEMED);
            assertThat(tx(() -> subscriptions.findByPublicId(redeemed.getSubscriptionId()))).isPresent();
            assertThat(tx(() -> subscriptions.findAll().stream().filter(s -> Set.of(one.tenantId(), two.tenantId()).contains(s.getTenantId())).count())).isEqualTo(1);
        }
    }

    @Test void tenTokenCollisionsRetryAfterRollbackWithoutCreatingOrphanIntents() {
        for (int i = 0; i < 10; i++) {
            Plan plan = plan(PlanType.EXAM_PACKAGE);
            LicenseIssueReceipt original = service.issue(plan.getPublicId(), null, UUID.randomUUID(), admin);
            String collision = tx(() -> codes.findByPublicId(original.publicId()).orElseThrow().getCode());
            String unique = "NEW-" + UUID.randomUUID().toString().substring(0, 20).toUpperCase(Locale.ROOT);
            doReturn(collision, unique).when(generator).generate();
            try {
                LicenseIssueReceipt result = service.issue(plan.getPublicId(), null, UUID.randomUUID(), admin);
                assertThat(result.publicId()).isNotEqualTo(original.publicId());
                assertThat(tx(() -> codes.findAll().stream().filter(c -> c.getPlanId().equals(plan.getPublicId())).count())).isEqualTo(2);
            } finally { reset(generator); }
        }
    }

    @Test void linkageFailureRollsBackClaimAndInsertedSubscription() {
        Plan plan = plan(PlanType.EXAM_PACKAGE);
        LicenseIssueReceipt receipt = service.issue(plan.getPublicId(), null, UUID.randomUUID(), admin);
        LicenseCode code = tx(() -> codes.findByPublicId(receipt.publicId()).orElseThrow());
        UUID tenant = UUID.randomUUID();
        doReturn(Optional.empty()).when(subscriptionSpy).findByLicenseKey(anyString());
        try {
            assertThatThrownBy(() -> service.redeem(code.getCode(), new CurrentUser(UUID.randomUUID(), tenant, List.of("HOST_ADMIN"))))
                    .isInstanceOf(LicenseCodeException.class);
            LicenseCode retained = tx(() -> codes.findByPublicId(code.getPublicId()).orElseThrow());
            assertThat(retained.getStatus()).isEqualTo(LicenseCodeStatus.ISSUED);
            assertThat(retained.getSubscriptionId()).isNull();
            assertThat(tx(() -> subscriptions.findAll().stream().filter(s -> s.getTenantId().equals(tenant)).count())).isZero();
        } finally { reset(subscriptionSpy); }
    }

    @Test void databaseRedeemPredicateRejectsExpiryAtOrBeforeExactMicrosecondBoundary() {
        Plan plan = plan(PlanType.EXAM_PACKAGE);
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        for (long offset : new long[]{-1, 0, 1}) {
            LicenseCode code = tx(() -> codes.saveAndFlush(LicenseCode.issue(
                    "BOUND-" + UUID.randomUUID().toString().substring(0, 20), plan.getPublicId(), admin.userId(), now.minusSeconds(60),
                    now.plus(offset, java.time.temporal.ChronoUnit.MICROS))));
            int updated = tx(() -> codes.markRedeemed(code.getCode(), UUID.randomUUID(), now,
                    LicenseCodeStatus.ISSUED, LicenseCodeStatus.REDEEMED));
            assertThat(updated).isEqualTo(offset > 0 ? 1 : 0);
            assertThat(tx(() -> codes.findByPublicId(code.getPublicId()).orElseThrow().getStatus()))
                    .isEqualTo(offset > 0 ? LicenseCodeStatus.REDEEMED : LicenseCodeStatus.ISSUED);
        }
    }

    @Test void failureAfterBothInsertsStillRollsBackSuccessfulIntentAndCode() {
        Plan plan = plan(PlanType.EXAM_PACKAGE); UUID key = UUID.randomUUID();
        jdbc.execute("""
                create or replace function migration_clean.phase04_fail_intent_insert()
                returns trigger language plpgsql as $$
                begin
                    raise exception 'phase04 injected failure after intent insert';
                end;
                $$
                """);
        jdbc.execute("""
                create trigger phase04_fail_intent_insert_trigger
                after insert on migration_clean.license_issue_intents
                for each row execute function migration_clean.phase04_fail_intent_insert()
                """);
        try {
            assertThatThrownBy(() -> service.issue(plan.getPublicId(), null, key, admin)).isInstanceOf(RuntimeException.class);
            assertThat(tx(() -> codes.findAll().stream().filter(c -> c.getPlanId().equals(plan.getPublicId())).count())).isZero();
            assertThat(tx(() -> intents.findByActorPublicIdAndOperationAndIdempotencyKey(admin.userId(), "ISSUE_LICENSE_CODE", key))).isEmpty();
        } finally {
            jdbc.execute("drop trigger if exists phase04_fail_intent_insert_trigger on migration_clean.license_issue_intents");
            jdbc.execute("drop function if exists migration_clean.phase04_fail_intent_insert()");
        }
    }

    @Test void migrationPreservesLegacyLicenseRowsAndAddsOnlyIntentStorage() {
        String schema = "phase04_legacy_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(URL, "codex_lifecycle_test", "").schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration").target("78").load().migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:55439/lifecycle_test?currentSchema=" + schema, "codex_lifecycle_test", ""));
        UUID id = UUID.randomUUID();
        jdbc.update("insert into license_codes(public_id,created_at,updated_at,code,plan_id,status,issued_by,issued_at) values (?,now(),now(),?,?,'ISSUED',?,now())",
                id, "LEGACY-MIGRATION-FIXTURE", UUID.randomUUID(), admin.userId());
        Flyway.configure().dataSource(URL, "codex_lifecycle_test", "").schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForObject("select count(*) from license_codes where public_id = ?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from license_issue_intents", Long.class)).isZero();
    }
}
