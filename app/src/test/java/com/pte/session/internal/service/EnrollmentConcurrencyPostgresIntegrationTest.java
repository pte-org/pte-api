package com.pte.session.internal.service;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.session.domain.Enrollment;
import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Independent committed transactions against the dedicated PostgreSQL lifecycle database.
 * The test is opt-in because it must never point at a developer's ordinary database.
 */
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "logging.level.root=ERROR",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.springframework=ERROR"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({IdentityService.class, EnrollmentConcurrencyPostgresIntegrationTest.TestBeans.class})
@EnabledIfSystemProperty(named = "lifecycle.test.db.url", matches = ".+")
@Timeout(120)
class EnrollmentConcurrencyPostgresIntegrationTest {

    private static final String URL =
            "jdbc:postgresql://127.0.0.1:55439/lifecycle_test?currentSchema=migration_clean";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (!URL.equals(System.getProperty("lifecycle.test.db.url"))) {
            throw new IllegalArgumentException("Dedicated test DB required");
        }
        Flyway.configure()
                .dataSource(URL, "codex_lifecycle_test", "")
                .schemas("migration_clean")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> "codex_lifecycle_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private ExamSessionRepository sessionRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void overlappingTransactionsSerializeOnStudentAndOnlyOneEnrolls() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = createStudent(tenantId);
        SessionPair sessions = createSessions(tenantId,
                Instant.parse("2036-01-01T08:00:00Z"), Instant.parse("2036-01-01T10:00:00Z"),
                Instant.parse("2036-01-01T09:00:00Z"), Instant.parse("2036-01-01T11:00:00Z"));
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondAttemptingLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = pool.submit(() -> tx(() -> {
                ExamSession session = sessionRepository.findById(sessions.firstId()).orElseThrow();
                identityService.lockActiveStudentsInTenant(List.of(studentId), tenantId);
                firstLocked.countDown();
                await(secondAttemptingLock);
                return enrollIfNoConflict(session, tenantId, studentId);
            }));
            Future<Boolean> second = pool.submit(() -> {
                await(firstLocked);
                return tx(() -> {
                    secondAttemptingLock.countDown();
                    ExamSession session = sessionRepository.findById(sessions.secondId()).orElseThrow();
                    identityService.lockActiveStudentsInTenant(List.of(studentId), tenantId);
                    return enrollIfNoConflict(session, tenantId, studentId);
                });
            });

            assertThat(first.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(30, TimeUnit.SECONDS)).isFalse();
        }

        assertThat(tx(() -> enrollmentRepository.findAll().stream()
                .filter(enrollment -> enrollment.getStudentPublicId().equals(studentId))
                .count())).isEqualTo(1);
    }

    @Test
    void oppositeBatchInputOrdersCompleteWithoutDeadlock() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID firstStudentId = createStudent(tenantId);
        UUID secondStudentId = createStudent(tenantId);
        SessionPair sessions = createSessions(tenantId,
                Instant.parse("2036-02-01T08:00:00Z"), Instant.parse("2036-02-01T10:00:00Z"),
                Instant.parse("2036-02-01T09:00:00Z"), Instant.parse("2036-02-01T11:00:00Z"));
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondAttemptingLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = pool.submit(() -> tx(() -> {
                identityService.lockActiveStudentsInTenant(
                        List.of(secondStudentId, firstStudentId), tenantId);
                firstLocked.countDown();
                await(secondAttemptingLock);
                ExamSession session = sessionRepository.findById(sessions.firstId()).orElseThrow();
                return enrollIfNoConflict(session, tenantId, firstStudentId)
                        && enrollIfNoConflict(session, tenantId, secondStudentId);
            }));
            Future<Boolean> second = pool.submit(() -> {
                await(firstLocked);
                return tx(() -> {
                    secondAttemptingLock.countDown();
                    identityService.lockActiveStudentsInTenant(
                            List.of(firstStudentId, secondStudentId), tenantId);
                    ExamSession session = sessionRepository.findById(sessions.secondId()).orElseThrow();
                    return !enrollIfNoConflict(session, tenantId, firstStudentId)
                            && !enrollIfNoConflict(session, tenantId, secondStudentId);
                });
            });

            assertThat(first.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(tx(() -> enrollmentRepository.findAll().stream()
                .filter(enrollment -> Set.of(firstStudentId, secondStudentId)
                        .contains(enrollment.getStudentPublicId()))
                .count())).isEqualTo(2);
    }

    @Test
    void touchingWindowBoundaryDoesNotCreateConflict() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = createStudent(tenantId);
        SessionPair sessions = createSessions(tenantId,
                Instant.parse("2036-03-01T08:00:00Z"), Instant.parse("2036-03-01T10:00:00Z"),
                Instant.parse("2036-03-01T10:00:00Z"), Instant.parse("2036-03-01T12:00:00Z"));

        tx(() -> {
            ExamSession first = sessionRepository.findById(sessions.firstId()).orElseThrow();
            identityService.lockActiveStudentsInTenant(List.of(studentId), tenantId);
            assertThat(enrollIfNoConflict(first, tenantId, studentId)).isTrue();
            return null;
        });
        Boolean secondCreated = tx(() -> {
            ExamSession second = sessionRepository.findById(sessions.secondId()).orElseThrow();
            identityService.lockActiveStudentsInTenant(List.of(studentId), tenantId);
            return enrollIfNoConflict(second, tenantId, studentId);
        });

        assertThat(secondCreated).isTrue();
        assertThat(tx(() -> enrollmentRepository.findAll().stream()
                .filter(enrollment -> enrollment.getStudentPublicId().equals(studentId))
                .count())).isEqualTo(2);
    }

    @Test
    void retryOnSameSessionKeepsOneEnrollment() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = createStudent(tenantId);
        SessionPair sessions = createSessions(tenantId,
                Instant.parse("2036-04-01T08:00:00Z"), Instant.parse("2036-04-01T10:00:00Z"),
                Instant.parse("2036-04-01T12:00:00Z"), Instant.parse("2036-04-01T14:00:00Z"));

        assertThat(tx(() -> {
            ExamSession session = sessionRepository.findById(sessions.firstId()).orElseThrow();
            identityService.lockActiveStudentsInTenant(List.of(studentId), tenantId);
            return enrollIfNoConflict(session, tenantId, studentId);
        })).isTrue();
        assertThat(tx(() -> {
            ExamSession session = sessionRepository.findById(sessions.firstId()).orElseThrow();
            identityService.lockActiveStudentsInTenant(List.of(studentId), tenantId);
            return enrollIfNoConflict(session, tenantId, studentId);
        })).isFalse();
        assertThat(tx(() -> enrollmentRepository.findBySessionIdAndStudentPublicIdIn(
                sessions.firstId(), List.of(studentId)))).hasSize(1);
    }

    private boolean enrollIfNoConflict(ExamSession session, UUID tenantId, UUID studentId) {
        if (!enrollmentRepository.findBySessionIdAndStudentPublicIdIn(session.getId(), List.of(studentId))
                .isEmpty()) {
            return false;
        }
        if (!enrollmentRepository.findConflictingEnrollments(tenantId, List.of(studentId),
                session.getOpensAt(), session.getClosesAt()).isEmpty()) {
            return false;
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setSession(session);
        enrollment.setStudentPublicId(studentId);
        enrollment.setTenantId(tenantId);
        enrollment.setLicenseKey(session.getLicenseKey());
        enrollmentRepository.saveAndFlush(enrollment);
        return true;
    }

    private UUID createStudent(UUID tenantId) {
        return tx(() -> {
            User user = new User();
            user.setUsername("phase15-" + UUID.randomUUID());
            user.setTenantId(tenantId);
            user.setStatus(com.pte.identity.domain.UserStatus.ACTIVE);
            user.setRoles(Set.of(Role.STUDENT));
            return userRepository.saveAndFlush(user).getPublicId();
        });
    }

    private SessionPair createSessions(UUID tenantId, Instant firstOpensAt, Instant firstClosesAt,
            Instant secondOpensAt, Instant secondClosesAt) {
        return tx(() -> {
            ExamSession first = session(tenantId, firstOpensAt, firstClosesAt);
            ExamSession second = session(tenantId, secondOpensAt, secondClosesAt);
            first = sessionRepository.saveAndFlush(first);
            second = sessionRepository.saveAndFlush(second);
            return new SessionPair(first.getId(), second.getId());
        });
    }

    private ExamSession session(UUID tenantId, Instant opensAt, Instant closesAt) {
        ExamSession session = new ExamSession();
        session.setName("Phase 15 concurrency test");
        session.setSessionCode("P15-" + UUID.randomUUID().toString().substring(0, 8));
        session.setTenantId(tenantId);
        session.setSubscriptionId(UUID.randomUUID());
        session.setLicenseKey("P15-TEST-LICENSE");
        session.setSnapshotPublicId(UUID.randomUUID());
        session.setTemplatePublicId(UUID.randomUUID());
        session.setTemplateVersion(1);
        session.setOpensAt(opensAt);
        session.setClosesAt(closesAt);
        session.setStatus(SessionStatus.SCHEDULED);
        session.setCapacity(50);
        session.setPolicy(ExamPolicy.realExamDefault());
        return session;
    }

    private <T> T tx(Supplier<T> action) {
        return new TransactionTemplate(transactions).execute(status -> action.get());
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while coordinating concurrency test", ex);
        }
    }

    private record SessionPair(Long firstId, Long secondId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        PasswordEncoder passwordEncoder() {
            return NoOpPasswordEncoder.getInstance();
        }
    }
}
