package com.pte.itembank.internal.generation.batch;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V53 against a schema that already holds V52-era jobs: the upgrade keeps them, and the recreated CHECK and indexes
 * behave as the batch design needs. Runs in its own schema of the dedicated database, dropped afterwards.
 */
@EnabledIfSystemProperty(named = BatchPostgresDatabase.URL_PROPERTY, matches = ".+")
@Timeout(120)
class BatchMigrationPostgresIntegrationTest {

    private static final String SCHEMA = "v52_upgrade";
    private static final UUID LEGACY_REQUESTER = UUID.randomUUID();

    @BeforeAll
    static void upgradeFromV52() throws SQLException {
        execute("drop schema if exists " + SCHEMA + " cascade");
        BatchPostgresDatabase.migrate(SCHEMA, "52");
        // V52-era rows: a finished job and an active one (the old index admitted exactly one active job).
        execute(legacyJob(LEGACY_REQUESTER, "COMPLETED"));
        execute(legacyJob(LEGACY_REQUESTER, "RUNNING"));
        BatchPostgresDatabase.migrate(SCHEMA, "latest");
    }

    @AfterAll
    static void dropSchema() throws SQLException {
        execute("drop schema if exists " + SCHEMA + " cascade");
    }

    @Test
    void theUpgradeKeepsTheExistingJobsAsStandaloneJobs() throws SQLException {
        assertThat(count("select count(*) from question_generation_jobs where requested_by_user_public_id = '"
                + LEGACY_REQUESTER + "' and batch_id is null and batch_seq is null")).isEqualTo(2);
        assertThat(count("select count(*) from flyway_schema_history where version = '53' and success")).isEqualTo(1);
    }

    @Test
    void theStatusCheckAcceptsPendingAndStillRejectsUnknownStatuses() throws SQLException {
        UUID batchId = batch(UUID.randomUUID());
        execute(child(UUID.randomUUID(), batchId, 0, "PENDING"));

        assertThatThrownBy(() -> execute(job(UUID.randomUUID(), "WAITING", true))).isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_qgen_jobs_status");
    }

    @Test
    void aPreviewAndASavingJobOfTheSameAuthorMayRunTogetherButNotTwoSavingJobs() throws SQLException {
        UUID author = UUID.randomUUID();
        execute(job(author, "RUNNING", false));
        execute(job(author, "QUEUED", true));

        assertThatThrownBy(() -> execute(job(author, "QUEUED", true))).isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_qgen_jobs_one_active_per_requester");
    }

    @Test
    void pendingBatchChildrenStayOutsideTheOneActiveJobIndex() throws SQLException {
        UUID author = UUID.randomUUID();
        UUID batchId = batch(author);
        execute(child(author, batchId, 0, "QUEUED"));
        execute(child(author, batchId, 1, "PENDING"));
        execute(child(author, batchId, 2, "PENDING"));

        assertThat(count("select count(*) from question_generation_jobs where requested_by_user_public_id = '"
                + author + "'")).isEqualTo(3);
    }

    @Test
    void severalChildrenOfOneBatchMayRunAtOnceButAStandaloneJobStaysAlone() throws SQLException {
        UUID author = UUID.randomUUID();
        UUID batchId = batch(author);
        execute(child(author, batchId, 0, "RUNNING"));
        execute(child(author, batchId, 1, "QUEUED"));
        execute(job(author, "QUEUED", true));

        assertThatThrownBy(() -> execute(job(author, "QUEUED", true))).isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_qgen_jobs_one_active_per_requester");
    }

    @Test
    void theBatchFieldsAreAllSetOrAllNull() throws SQLException {
        String partial = job(UUID.randomUUID(), "COMPLETED", true)
                .replace("save_questions)", "save_questions, batch_seq)").replace("true)", "true, 0)");

        assertThatThrownBy(() -> execute(partial)).isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_qgen_jobs_batch_fields");
    }

    @Test
    void anAuthorHasAtMostOneOpenBatch() throws SQLException {
        UUID author = UUID.randomUUID();
        batch(author);

        assertThatThrownBy(() -> batch(author)).isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_qgen_batches_one_open_per_requester");
    }

    private static UUID batch(UUID author) throws SQLException {
        UUID publicId = UUID.randomUUID();
        execute("insert into question_generation_batches (public_id, created_at, updated_at, requested_by_user_public_id, "
                + "pool, rows, total_requested, status) values ('" + publicId + "', now(), now(), '" + author
                + "', 'PRACTICE', '[]'::jsonb, 3, 'QUEUED')");
        return publicId;
    }

    private static String legacyJob(UUID author, String status) {
        return "insert into question_generation_jobs (public_id, created_at, updated_at, requested_by_user_public_id, "
                + "task_type_key, cefr_level, pool, requested_count, status) values (gen_random_uuid(), now(), now(), '"
                + author + "', 'MC_READING_SINGLE', 'B2', 'PRACTICE', 2, '" + status + "')";
    }

    private static String job(UUID author, String status, boolean saves) {
        return "insert into question_generation_jobs (public_id, created_at, updated_at, requested_by_user_public_id, "
                + "task_type_key, cefr_level, pool, requested_count, status, save_questions) values (gen_random_uuid(), "
                + "now(), now(), '" + author + "', 'MC_READING_SINGLE', 'B2', 'PRACTICE', 2, '" + status + "', "
                + saves + ")";
    }

    private static String child(UUID author, UUID batchPublicId, int seq, String status) {
        return "insert into question_generation_jobs (public_id, created_at, updated_at, requested_by_user_public_id, "
                + "task_type_key, cefr_level, pool, requested_count, status, batch_id, batch_seq, batch_row_index) "
                + "select gen_random_uuid(), now(), now(), '" + author + "', 'MC_READING_SINGLE', 'B2', 'PRACTICE', 1, '"
                + status + "', id, " + seq + ", 0 from question_generation_batches where public_id = '"
                + batchPublicId + "'";
    }

    private static long count(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(BatchPostgresDatabase.url(), BatchPostgresDatabase.user(),
                BatchPostgresDatabase.password());
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + SCHEMA);
        }
        return connection;
    }
}
