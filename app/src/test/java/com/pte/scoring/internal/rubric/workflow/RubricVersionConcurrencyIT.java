package com.pte.scoring.internal.rubric.workflow;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricActivationAudit;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.rubric.repository.*;
import com.pte.scoring.internal.rubric.service.*;
import com.pte.scoring.internal.rubric.workflow.dto.RubricStatusRequest;
import com.pte.shared.security.CurrentUser;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** Actual Spring Data JPA repositories, ORM flush order, real PostgreSQL indexes/triggers and transaction retries. */
@Testcontainers(disabledWithoutDocker = true)
class RubricVersionConcurrencyIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static SessionFactory factory;
    private static JdbcTemplate jdbc;
    private ScoringRubricVersionRepository versions;
    private RubricVersionService lifecycle;
    private RubricActivationWorkflow activation;
    private RubricDiffService diffs;
    private TransactionTemplate transaction;
    private final CurrentUser manager = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_MANAGER"));

    @BeforeAll static void startPersistence() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        factory = new Configuration().addAnnotatedClass(ScoringRubricVersion.class).addAnnotatedClass(ScoringRubricActivationAudit.class)
                .setProperty("jakarta.persistence.jdbc.url", POSTGRES.getJdbcUrl())
                .setProperty("jakarta.persistence.jdbc.user", POSTGRES.getUsername())
                .setProperty("jakarta.persistence.jdbc.password", POSTGRES.getPassword())
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .buildSessionFactory();
    }
    @AfterAll static void closePersistence() { if (factory != null) factory.close(); }

    @BeforeEach void services() {
        var entityManager = SharedEntityManagerCreator.createSharedEntityManager(factory);
        var repositoryFactory = new JpaRepositoryFactory(entityManager);
        versions = repositoryFactory.getRepository(ScoringRubricVersionRepository.class);
        var audits = repositoryFactory.getRepository(ScoringRubricActivationAuditRepository.class);
        var transactions = new JpaTransactionManager(factory);
        transaction = new TransactionTemplate(transactions);
        lifecycle = new RubricVersionService(versions, audits, new RubricParametersValidator(CATALOG, CODEC),
                Clock.fixed(RubricWorkflowFixture.NOW, ZoneOffset.UTC));
        diffs = new RubricDiffService(versions);
        activation = new RubricActivationWorkflow(versions, lifecycle, diffs, transactions);
    }

    @Test void nativeRetireFlushPrecedesActivationAndWritesOneAudit() {
        var draft = draft();
        int before = jdbc.queryForObject("select count(*) from scoring_rubric_activation_audits", Integer.class);
        assertThatThrownBy(() -> jdbc.update("update scoring_rubric_versions set status='ACTIVE' where public_id=?", draft.getPublicId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var request = confirmation(draft);
        activation.activate(draft.getPublicId(), request, manager);
        assertThat(jdbc.queryForObject("select count(*) from scoring_rubric_versions where status='ACTIVE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select approver_user_public_id from scoring_rubric_versions where public_id=?", UUID.class, draft.getPublicId()))
                .isEqualTo(manager.userId());
        assertThat(jdbc.queryForObject("select count(*) from scoring_rubric_activation_audits", Integer.class)).isEqualTo(before + 1);
    }

    @Test void twoConcurrentConfirmationsProduceExactlyOneNewActiveAndAudit() throws Exception {
        var first = draft(); var second = draft();
        var firstConfirmation = confirmation(first); var secondConfirmation = confirmation(second);
        int before = jdbc.queryForObject("select count(*) from scoring_rubric_activation_audits", Integer.class);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> a = workers.submit(() -> activateAtOnce(first, firstConfirmation, start));
            Future<Boolean> b = workers.submit(() -> activateAtOnce(second, secondConfirmation, start));
            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("select count(*) from scoring_rubric_versions where status='ACTIVE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from scoring_rubric_activation_audits", Integer.class)).isEqualTo(before + 1);
    }

    @Test void existingExamPinAndPublishedSnapshotRemainByteForByteUnchanged() {
        var active = transaction.execute(status -> versions.findByStatus(RubricVersionStatus.ACTIVE).orElseThrow());
        UUID examId = UUID.randomUUID(); UUID resultId = UUID.randomUUID();
        String snapshot = "{\"schemaVersion\":2,\"rubricVersionPublicId\":\"" + active.getPublicId() + "\",\"scoreInputs\":[]}";
        jdbc.update("insert into exam_definitions(public_id,created_at,updated_at,deleted,lock_version,code,version,name,status,template_policy,rubric_version_public_id) "
                + "values (?,now(),now(),false,0,?,1,'Synthetic pinned template','DRAFT','CUSTOM',?)", examId, examId.toString(), active.getPublicId());
        jdbc.update("insert into exam_results(public_id,created_at,updated_at,deleted,attempt_public_id,session_public_id,student_public_id,tenant_id,published,report_snapshot_json) "
                + "values (?,now(),now(),false,?,?,?,?,true,?)", resultId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), snapshot);
        var draft = draft();
        activation.activate(draft.getPublicId(), confirmation(draft), manager);
        assertThat(jdbc.queryForObject("select rubric_version_public_id from exam_definitions where public_id=?", UUID.class, examId)).isEqualTo(active.getPublicId());
        assertThat(jdbc.queryForObject("select report_snapshot_json from exam_results where public_id=?", String.class, resultId)).isEqualTo(snapshot);
    }

    @Test void discardedDraftIsHiddenAndItsVersionNumberIsNeverReused() {
        var removed = draft();
        transaction.execute(status -> {
            var row = versions.findByPublicId(removed.getPublicId()).orElseThrow();
            row.setDeleted(true);
            versions.saveAndFlush(row);
            return null;
        });
        Optional<ScoringRubricVersion> hidden = transaction.execute(status -> versions.findByPublicId(removed.getPublicId()));
        assertThat(hidden).isEmpty();
        assertThat(draft().getVersionNo()).isGreaterThan(removed.getVersionNo());
    }

    private ScoringRubricVersion draft() {
        return transaction.execute(status -> lifecycle.createDraft(seedJson(), 1, UUID.randomUUID()));
    }
    private RubricStatusRequest confirmation(ScoringRubricVersion draft) {
        var diff = transaction.execute(status -> diffs.read(draft.getPublicId()));
        return new RubricStatusRequest(RubricStatusRequest.Action.ACTIVATE, null, diff.expectedActiveVersionPublicId(), diff.diffDigest());
    }
    private boolean activateAtOnce(ScoringRubricVersion draft, RubricStatusRequest request, CountDownLatch start) throws InterruptedException {
        start.await();
        try { activation.activate(draft.getPublicId(), request, manager); return true; }
        catch (com.pte.scoring.internal.exception.RubricVersionException conflict) {
            assertThat(conflict.getCode()).isIn("RUBRIC_DIFF_STALE", "RUBRIC_VERSION_CONCURRENT_CHANGE");
            return false;
        }
    }
}
