package com.pte.billing.internal.service;

import com.pte.billing.domain.enums.TenantApplicationStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.mapper.TenantApplicationMapper;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.audit.AuditLogService;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/** Opt-in local PostgreSQL proof that review decisions serialize on the application row. */
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "logging.level.root=ERROR",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.springframework=ERROR"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(TenantApplicationService.class)
@EnabledIfSystemProperty(named = "lifecycle.test.db.url", matches = ".+")
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class TenantApplicationReviewPostgresIntegrationTest {
    private static final String URL = "jdbc:postgresql://127.0.0.1:55439/lifecycle_test?currentSchema=migration_clean";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (!URL.equals(System.getProperty("lifecycle.test.db.url"))) {
            throw new IllegalArgumentException("Dedicated test DB required");
        }
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> "codex_lifecycle_test");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private OrganizationRepository applications;

    @Autowired
    private TenantApplicationService service;

    @Autowired
    private PlatformTransactionManager transactions;

    @MockitoBean
    private TenancyService tenancyService;

    @MockitoBean
    private IdentityService identityService;

    @MockitoBean
    private PlatformSettingService platformSettingService;

    @MockitoBean
    private ApplicationEventPublisher eventPublisher;

    @MockitoBean
    private AuditLogService auditLogService;

    @Test
    void tenApproveRejectRacesCommitExactlyOneTerminalDecision() throws Exception {
        when(platformSettingService.getInteger(BillingConstants.FREE_STUDENT_LIMIT_SETTING_KEY)).thenReturn(75);
        delegateApplicationReviewToRepository();
        when(identityService.createHostAdmin(any(), anyString()))
                .thenAnswer(invocation -> new HostAdminCreated(hostAdmin(invocation.getArgument(1)), "one-time"));

        for (int i = 0; i < 10; i++) {
            Organization application = pending("race-" + i);
            clearInvocations(tenancyService, identityService, eventPublisher);
            CurrentUser approver = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
            CurrentUser rejector = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

            List<Throwable> outcomes = race(
                    () -> service.approve(application.getPublicId(), approver),
                    () -> service.reject(application.getPublicId(),
                            new com.pte.billing.internal.dto.request.application.RejectApplicationRequest("duplicate review"),
                            rejector));

            assertThat(outcomes.stream().filter(value -> value == null).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(value -> value != null).count()).isEqualTo(1);
            Organization persisted = tx(() -> applications.findByPublicId(application.getPublicId()).orElseThrow());
            assertThat(TenantApplicationMapper.statusOf(persisted)).isIn(TenantApplicationStatus.APPROVED,
                    TenantApplicationStatus.REJECTED);
            if (TenantApplicationMapper.statusOf(persisted) == TenantApplicationStatus.APPROVED) {
                verify(tenancyService, times(1)).approveApplication(any(Organization.class), any(), anyInt());
                verify(identityService, times(1)).createHostAdmin(any(), anyString());
            } else {
                verify(tenancyService, never()).approveApplication(any(Organization.class), any(), anyInt());
                verify(identityService, never()).createHostAdmin(any(), anyString());
            }
        }
    }

    @Test
    void approvalFailureLeavesPendingApplication() {
        Organization application = pending("failure");
        when(platformSettingService.getInteger(BillingConstants.FREE_STUDENT_LIMIT_SETTING_KEY)).thenReturn(75);
        delegateApplicationReviewToRepository();
        when(identityService.createHostAdmin(any(), anyString()))
                .thenThrow(new IllegalStateException("identity unavailable"));

        assertThatThrownBy(() -> service.approve(application.getPublicId(),
                new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(tx(() -> applications.findByPublicId(application.getPublicId()).orElseThrow().getStatus()))
                .isEqualTo(OrganizationStatus.PENDING);
    }

    @Test
    void pendingRequestedCodeIsUniqueUntilTheApplicationReachesATerminalState() {
        String code = "partial-" + UUID.randomUUID().toString().substring(0, 8);
        Organization first = pendingWithCode(code);

        assertThatThrownBy(() -> tx(() -> applications.saveAndFlush(application(code))))
                .isInstanceOf(DataIntegrityViolationException.class);

        tx(() -> {
            Organization persisted = applications.findByPublicId(first.getPublicId()).orElseThrow();
            persisted.setStatus(OrganizationStatus.REJECTED);
            applications.saveAndFlush(persisted);
            return null;
        });

        assertThatCode(() -> tx(() -> applications.saveAndFlush(application(code))))
                .doesNotThrowAnyException();
    }

    private Organization pending(String suffix) {
        String unique = suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return pendingWithCode("review-" + unique);
    }

    private Organization pendingWithCode(String code) {
        return tx(() -> applications.saveAndFlush(application(code)));
    }

    private Organization application(String code) {
        Organization application = new Organization();
        application.setName("Review fixture " + code);
        application.setOrganizationType("SCHOOL");
        application.setCode(code);
        application.setContactEmail("review-" + code + "@example.test");
        application.setTaxCode("TAX-" + code);
        return application;
    }

    /** The mocked tenancy module still reads and decides the real application rows, as the production one does. */
    private void delegateApplicationReviewToRepository() {
        when(tenancyService.findApplicationForUpdate(any()))
                .thenAnswer(invocation -> applications.findApplicationForUpdate(invocation.getArgument(0)));
        when(tenancyService.approveApplication(any(Organization.class), any(), anyInt())).thenAnswer(invocation -> {
            Organization application = invocation.getArgument(0);
            application.setStudentLimit(invocation.getArgument(2));
            application.approve(invocation.getArgument(1), Instant.now());
            return applications.saveAndFlush(application);
        });
        when(tenancyService.rejectApplication(any(Organization.class), any(), any())).thenAnswer(invocation -> {
            Organization application = invocation.getArgument(0);
            application.reject(invocation.getArgument(1), Instant.now(), invocation.getArgument(2));
            return applications.saveAndFlush(application);
        });
    }

    private User hostAdmin(String username) {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername(username);
        user.setRoles(java.util.Set.of(Role.HOST_ADMIN));
        return user;
    }

    private <T> T tx(Supplier<T> action) {
        return new TransactionTemplate(transactions).execute(status -> action.get());
    }

    private List<Throwable> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable action : List.of(first, second)) {
                futures.add(executor.submit(contender(action, ready, start)));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return Arrays.asList(futures.get(0).get(30, TimeUnit.SECONDS),
                    futures.get(1).get(30, TimeUnit.SECONDS));
        }
    }

    private Callable<Throwable> contender(Runnable action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Race start timed out");
            }
            try {
                action.run();
                return null;
            } catch (RuntimeException failure) {
                return failure;
            }
        };
    }
}
