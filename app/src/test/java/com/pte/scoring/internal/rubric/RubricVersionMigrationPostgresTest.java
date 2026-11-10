package com.pte.scoring.internal.rubric;

import com.pte.scoring.internal.rubric.support.RubricPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V32 adds the rubric version table with its seed, the immutability triggers, the activation audit table and the
 * two nullable pins. Every rule here is enforced by the database itself, so it is checked against a real Postgres.
 */
@Testcontainers(disabledWithoutDocker = true)
class RubricVersionMigrationPostgresTest {

    private static final String BEFORE_RUBRIC_VERSIONS = "31";
    private static final UUID SEED_VERSION = UUID.fromString("6f1c2b7e-3d4a-4c5b-9e8f-000000000001");
    private static final List<String> TASK_TYPES = List.of("READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE",
            "RE_TELL_LECTURE", "SUMMARIZE_GROUP_DISCUSSION", "RESPOND_TO_A_SITUATION", "ANSWER_SHORT_QUESTION",
            "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT", "MC_READING_SINGLE",
            "MC_READING_MULTIPLE", "RE_ORDER_PARAGRAPHS", "FILL_IN_THE_BLANKS_DRAG_AND_DROP",
            "FILL_IN_THE_BLANKS_DROPDOWN", "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE",
            "FILL_IN_THE_BLANKS_TYPE_IN", "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD",
            "HIGHLIGHT_INCORRECT_WORDS", "WRITE_FROM_DICTATION");
    private static final String RETIRE = "UPDATE scoring_rubric_versions SET status = 'RETIRED', retired_at = NOW(), "
            + "updated_at = NOW(), lock_version = lock_version + 1 WHERE public_id = ?";
    private static final String ACTIVATE = "UPDATE scoring_rubric_versions SET status = 'ACTIVE', "
            + "activated_at = NOW(), approver_user_public_id = ?, updated_at = NOW() WHERE public_id = ?";
    private static final String UPDATE_SEED = "UPDATE scoring_rubric_versions SET %s WHERE public_id = ?";

    private static void onFullyMigratedDatabase(BiConsumer<JdbcTemplate, DataSource> body) {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(RubricPostgres.IMAGE)) {
            postgres.start();
            DataSource dataSource = RubricPostgres.dataSource(postgres);
            RubricPostgres.migrateAll(dataSource);
            body.accept(new JdbcTemplate(dataSource), dataSource);
        }
    }

    private static UUID insertVersion(JdbcTemplate jdbc, int versionNo, String status) {
        UUID publicId = UUID.randomUUID();
        jdbc.update("INSERT INTO scoring_rubric_versions (public_id, created_at, updated_at, deleted, lock_version, "
                + "version_no, status, parameters, catalog_version) "
                + "VALUES (?, NOW(), NOW(), FALSE, 0, ?, ?, '{}'::jsonb, 1)", publicId, versionNo, status);
        return publicId;
    }

    private static void insertExamDefinition(JdbcTemplate jdbc, String code, String status, String policy) {
        jdbc.update("INSERT INTO exam_definitions (public_id, created_at, updated_at, deleted, lock_version, code, "
                        + "version, name, status, template_policy) "
                        + "VALUES (?, NOW(), NOW(), FALSE, 0, ?, 1, 'IT definition', ?, ?)",
                UUID.randomUUID(), code, status, policy);
    }

    private static String statusOf(JdbcTemplate jdbc, UUID publicId) {
        return jdbc.queryForObject("SELECT status FROM scoring_rubric_versions WHERE public_id = ?", String.class,
                publicId);
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void backfillsEveryExistingExamDefinitionWithVersionOne() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(RubricPostgres.IMAGE)) {
            postgres.start();
            DataSource dataSource = RubricPostgres.dataSource(postgres);
            RubricPostgres.migrateTo(dataSource, BEFORE_RUBRIC_VERSIONS);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertExamDefinition(jdbc, "IT_DRAFT", "DRAFT", "CUSTOM");
            insertExamDefinition(jdbc, "IT_RETIRED", "RETIRED", "STANDARD_PTE");
            int before = count(jdbc, "SELECT count(*) FROM exam_definitions");
            assertThat(before).as("the baseline seed plus the two rows above").isGreaterThanOrEqualTo(3);

            RubricPostgres.migrateAll(dataSource);

            assertThat(count(jdbc, "SELECT count(*) FROM exam_definitions")).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_definitions "
                    + "WHERE rubric_version_public_id IS DISTINCT FROM ?", Integer.class, SEED_VERSION))
                    .as("no exam definition is left without the version 1 pin").isZero();
        }
    }

    @Test
    void appliesOnADatabaseWhoseExamDefinitionsTableIsEmpty() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(RubricPostgres.IMAGE)) {
            postgres.start();
            DataSource dataSource = RubricPostgres.dataSource(postgres);
            RubricPostgres.migrateTo(dataSource, BEFORE_RUBRIC_VERSIONS);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("TRUNCATE TABLE exam_definitions CASCADE");
            assertThat(count(jdbc, "SELECT count(*) FROM exam_definitions")).isZero();

            RubricPostgres.migrateAll(dataSource);

            assertThat(count(jdbc, "SELECT count(*) FROM exam_definitions")).isZero();
            assertThat(count(jdbc, "SELECT count(*) FROM scoring_rubric_versions")).isEqualTo(1);
        }
    }

    @Test
    void seedsExactlyOneActiveVersionOneHoldingAllTaskTypes() {
        onFullyMigratedDatabase((jdbc, dataSource) -> {
            assertThat(count(jdbc, "SELECT count(*) FROM scoring_rubric_versions")).isEqualTo(1);
            assertThat(count(jdbc, "SELECT count(*) FROM scoring_rubric_versions WHERE status = 'ACTIVE'"))
                    .isEqualTo(1);
            assertThat(count(jdbc, "SELECT version_no FROM scoring_rubric_versions WHERE status = 'ACTIVE'"))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT public_id FROM scoring_rubric_versions WHERE version_no = 1",
                    UUID.class)).isEqualTo(SEED_VERSION);
            assertThat(count(jdbc, "SELECT catalog_version FROM scoring_rubric_versions WHERE version_no = 1"))
                    .isEqualTo(1);
            assertThat(count(jdbc, "SELECT (parameters->>'azureBandMappingVersion')::int "
                    + "FROM scoring_rubric_versions WHERE version_no = 1")).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT jsonb_object_keys(parameters->'taskTypes') "
                    + "FROM scoring_rubric_versions WHERE version_no = 1", String.class))
                    .hasSize(22).containsExactlyInAnyOrderElementsOf(TASK_TYPES);
            assertThat(count(jdbc, "SELECT count(*) FROM scoring_rubric_versions v, "
                    + "jsonb_each(v.parameters->'taskTypes') t WHERE v.version_no = 1 "
                    + "AND jsonb_exists(t.value, 'traitWeights') AND jsonb_exists(t.value, 'traitScales') "
                    + "AND jsonb_exists(t.value, 'formBands') AND jsonb_exists(t.value, 'gateEnabled')"))
                    .as("every task type carries the four parameter groups").isEqualTo(22);
        });
    }

    @Test
    void refusesASecondActiveVersion() {
        onFullyMigratedDatabase((jdbc, dataSource) -> {
            assertThatThrownBy(() -> insertVersion(jdbc, 2, "ACTIVE")).isInstanceOf(DuplicateKeyException.class)
                    .hasMessageContaining("ux_scoring_rubric_versions_single_active");
            // Any number of non-ACTIVE rows is fine.
            insertVersion(jdbc, 2, "DRAFT");
            insertVersion(jdbc, 3, "PENDING");
            insertVersion(jdbc, 4, "REJECTED");
            assertThat(count(jdbc, "SELECT count(*) FROM scoring_rubric_versions WHERE status = 'ACTIVE'"))
                    .isEqualTo(1);
        });
    }

    @Test
    void anActiveVersionMayOnlyBeRetiredNeverEdited() {
        onFullyMigratedDatabase((jdbc, dataSource) -> {
            List<String> edits = List.of(
                    "parameters = '{\"taskTypes\": {}}'::jsonb",
                    "catalog_version = 2",
                    "version_no = 9",
                    "author_user_public_id = gen_random_uuid()",
                    "approver_user_public_id = gen_random_uuid()",
                    "activated_at = NOW() + INTERVAL '1 day'",
                    "rejection_reason = 'no'",
                    "deleted = TRUE",
                    "status = 'DRAFT'",
                    "status = 'PENDING'");
            for (String edit : edits) {
                assertThatThrownBy(() -> jdbc.update(UPDATE_SEED.formatted(edit), SEED_VERSION))
                        .as(edit).isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("immutable");
            }
            assertThatThrownBy(() -> jdbc.update(UPDATE_SEED.formatted(
                    "status = 'RETIRED', retired_at = NOW(), parameters = '{}'::jsonb"), SEED_VERSION))
                    .as("retiring while editing parameters").isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("immutable");
            assertThatThrownBy(() -> jdbc.update(UPDATE_SEED.formatted(
                    "status = 'RETIRED', activated_at = NOW() + INTERVAL '1 day'"), SEED_VERSION))
                    .as("retiring while touching activated_at").isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("immutable");
            assertThat(statusOf(jdbc, SEED_VERSION)).isEqualTo("ACTIVE");

            assertThat(jdbc.update(RETIRE, SEED_VERSION)).isEqualTo(1);

            assertThat(statusOf(jdbc, SEED_VERSION)).isEqualTo("RETIRED");
            assertThat(jdbc.queryForObject("SELECT retired_at IS NOT NULL FROM scoring_rubric_versions "
                    + "WHERE public_id = ?", Boolean.class, SEED_VERSION)).isTrue();
            assertThat(jdbc.queryForObject("SELECT lock_version FROM scoring_rubric_versions WHERE public_id = ?",
                    Long.class, SEED_VERSION)).isEqualTo(1L);
        });
    }

    @Test
    void aRetiredVersionCannotChange() {
        onFullyMigratedDatabase((jdbc, dataSource) -> {
            jdbc.update(RETIRE, SEED_VERSION);

            for (String edit : List.of("status = 'ACTIVE'", "status = 'DRAFT'", "status = 'RETIRED'",
                    "retired_at = NOW() + INTERVAL '1 day'", "updated_at = NOW()", "lock_version = 7",
                    "parameters = '{}'::jsonb", "catalog_version = 2")) {
                assertThatThrownBy(() -> jdbc.update(UPDATE_SEED.formatted(edit), SEED_VERSION))
                        .as(edit).isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("immutable");
            }
            assertThat(statusOf(jdbc, SEED_VERSION)).isEqualTo("RETIRED");
        });
    }

    @Test
    void anActiveOrRetiredVersionCannotBeDeleted() {
        onFullyMigratedDatabase((jdbc, dataSource) -> {
            assertThatThrownBy(() -> jdbc.update("DELETE FROM scoring_rubric_versions WHERE public_id = ?",
                    SEED_VERSION)).as("ACTIVE").isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("cannot be deleted");

            jdbc.update(RETIRE, SEED_VERSION);

            assertThatThrownBy(() -> jdbc.update("DELETE FROM scoring_rubric_versions WHERE public_id = ?",
                    SEED_VERSION)).as("RETIRED").isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("cannot be deleted");
            assertThat(count(jdbc, "SELECT count(*) FROM scoring_rubric_versions")).isEqualTo(1);
        });
    }

    @Test
    void draftPendingAndRejectedVersionsStayEditableButCannotBeRetired() {
        onFullyMigratedDatabase((jdbc, dataSource) -> {
            UUID draft = insertVersion(jdbc, 2, "DRAFT");
            UUID pending = insertVersion(jdbc, 3, "PENDING");

            assertThat(jdbc.update("UPDATE scoring_rubric_versions SET parameters = '{\"a\": 1}'::jsonb, "
                    + "catalog_version = 2, updated_at = NOW() WHERE public_id = ?", draft)).isEqualTo(1);
            assertThat(jdbc.update("UPDATE scoring_rubric_versions SET status = 'REJECTED', "
                    + "rejection_reason = 'no' WHERE public_id = ?", pending)).isEqualTo(1);
            for (UUID notActive : List.of(draft, pending)) {
                assertThatThrownBy(() -> jdbc.update(RETIRE, notActive))
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("only an ACTIVE version can be retired");
            }
            assertThat(jdbc.update("DELETE FROM scoring_rubric_versions WHERE public_id = ?", draft)).isEqualTo(1);
        });
    }

    @Test
    void aDraftBecomesActiveOnlyAfterThePreviousActiveIsRetired() {
        onFullyMigratedDatabase((jdbc, dataSource) -> {
            UUID next = insertVersion(jdbc, 2, "DRAFT");
            UUID approver = UUID.randomUUID();

            assertThatThrownBy(() -> jdbc.update(ACTIVATE, approver, next))
                    .as("activating while version 1 is still ACTIVE").isInstanceOf(DuplicateKeyException.class)
                    .hasMessageContaining("ux_scoring_rubric_versions_single_active");
            assertThat(statusOf(jdbc, next)).isEqualTo("DRAFT");

            jdbc.update(RETIRE, SEED_VERSION);
            assertThat(jdbc.update(ACTIVATE, approver, next)).isEqualTo(1);

            assertThat(statusOf(jdbc, next)).isEqualTo("ACTIVE");
            assertThat(statusOf(jdbc, SEED_VERSION)).isEqualTo("RETIRED");
            assertThat(count(jdbc, "SELECT count(*) FROM scoring_rubric_versions WHERE status = 'ACTIVE'"))
                    .isEqualTo(1);
        });
    }

    @Test
    void retiringTheOldVersionBeforeActivatingTheNewOneWorksInOneTransactionAndTheReverseOrderFails() {
        onFullyMigratedDatabase((jdbc, dataSource) -> {
            TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            UUID next = insertVersion(jdbc, 2, "DRAFT");
            UUID approver = UUID.randomUUID();

            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                jdbc.update(ACTIVATE, approver, next);
                jdbc.update(RETIRE, SEED_VERSION);
            })).as("activate first, retire second").isInstanceOf(DuplicateKeyException.class);
            assertThat(statusOf(jdbc, SEED_VERSION)).as("rolled back").isEqualTo("ACTIVE");
            assertThat(statusOf(jdbc, next)).as("rolled back").isEqualTo("DRAFT");

            tx.executeWithoutResult(status -> {
                jdbc.update(RETIRE, SEED_VERSION);
                jdbc.update(ACTIVATE, approver, next);
            });
            assertThat(statusOf(jdbc, SEED_VERSION)).isEqualTo("RETIRED");
            assertThat(statusOf(jdbc, next)).isEqualTo("ACTIVE");
        });
    }

    @Test
    void activationAuditsAreInsertOnly() {
        onFullyMigratedDatabase((jdbc, dataSource) -> {
            UUID audit = UUID.randomUUID();
            jdbc.update("INSERT INTO scoring_rubric_activation_audits (public_id, created_at, updated_at, deleted, "
                            + "version_public_id, previous_active_public_id, actor_user_public_id, activated_at, "
                            + "diff_summary) VALUES (?, NOW(), NOW(), FALSE, ?, NULL, ?, NOW(), 'first version')",
                    audit, SEED_VERSION, UUID.randomUUID());
            assertThat(count(jdbc, "SELECT count(*) FROM scoring_rubric_activation_audits")).isEqualTo(1);

            assertThatThrownBy(() -> jdbc.update("UPDATE scoring_rubric_activation_audits SET diff_summary = 'x' "
                    + "WHERE public_id = ?", audit)).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("insert-only");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM scoring_rubric_activation_audits "
                    + "WHERE public_id = ?", audit)).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("insert-only");
            assertThatThrownBy(() -> jdbc.execute("TRUNCATE TABLE scoring_rubric_activation_audits"))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("insert-only");

            assertThat(jdbc.queryForObject("SELECT diff_summary FROM scoring_rubric_activation_audits "
                    + "WHERE public_id = ?", String.class, audit)).isEqualTo("first version");
        });
    }

    @Test
    void thePinColumnsAreNullableOnExamDefinitionsAndAnswerEvaluations() {
        onFullyMigratedDatabase((jdbc, dataSource) -> {
            for (String table : List.of("exam_definitions", "answer_evaluations")) {
                assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = ? "
                                + "AND column_name = 'rubric_version_public_id'", String.class, table))
                        .as(table).isEqualTo("YES");
            }

            insertExamDefinition(jdbc, "IT_NO_PIN", "DRAFT", "CUSTOM");
            jdbc.update("INSERT INTO answer_evaluations (public_id, created_at, updated_at, deleted, lock_version, "
                            + "answer_public_id, attempt_public_id, session_public_id, tenant_id, source) "
                            + "VALUES (?, NOW(), NOW(), FALSE, 0, ?, ?, ?, ?, 'OBJECTIVE')",
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

            assertThat(count(jdbc, "SELECT count(*) FROM exam_definitions WHERE code = 'IT_NO_PIN' "
                    + "AND rubric_version_public_id IS NULL")).isEqualTo(1);
            assertThat(count(jdbc, "SELECT count(*) FROM answer_evaluations "
                    + "WHERE rubric_version_public_id IS NULL")).isEqualTo(1);
        });
    }
}
