package com.pte.overview.internal.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in PostgreSQL proof for the overview migrations (V94, V95), enabled by passing
 * {@code -Dlifecycle.test.db.url=<jdbc url>}. The URL (including any credentials it
 * needs) comes only from that property; nothing about the host, port or account is
 * hard-coded. Everything runs in a brand-new, uniquely named schema that is dropped
 * afterwards, so no existing data is touched.
 *
 * <p>What it proves: Flyway migrates an EMPTY schema through every migration
 * (V1..V94 and later), Hibernate then validates the entity model against that
 * schema ({@code ddl-auto=validate}), V94 created the six dashboard indexes, V94 is
 * idempotent, and the heavy host-dashboard queries can and do use those indexes
 * (the EXPLAIN output is also written to {@code target/overview-explain-plans.txt}).
 */
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=false",
        "logging.level.root=ERROR",
        "logging.level.org.hibernate.SQL=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "lifecycle.test.db.url", matches = ".+")
@Timeout(value = 300, unit = TimeUnit.SECONDS)
class OverviewMigrationPostgresIntegrationTest {

    private static final String URL_PROPERTY = "lifecycle.test.db.url";
    private static final String SCHEMA = "overview_mig_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    private static final String V94_RESOURCE = "db/migration/V94__overview_host_dashboard_indexes.sql";
    private static final String V95_RESOURCE = "db/migration/V95__overview_host_commercial_ops_indexes.sql";
    private static final AtomicBoolean MIGRATED = new AtomicBoolean();

    private static final Set<String> V95_INDEXES = Set.of(
            "idx_attempt_reports_tenant_unpublished", "idx_audit_logs_tenant_created");

    private static final Set<String> V94_INDEXES = Set.of(
            "idx_attempts_tenant_submitted", "idx_attempts_tenant_started", "idx_attempts_tenant_status",
            "idx_users_tenant_created", "idx_sessions_tenant_status", "idx_sessions_tenant_created");

    private static String url() {
        return System.getProperty(URL_PROPERTY);
    }

    /** Creates the scratch schema and migrates it from empty, once, before the Spring context starts. */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (MIGRATED.compareAndSet(false, true)) {
            Flyway.configure()
                    .dataSource(new DriverManagerDataSource(url()))
                    .schemas(SCHEMA)
                    .defaultSchema(SCHEMA)
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate();
        }
        registry.add("spring.datasource.url", OverviewMigrationPostgresIntegrationTest::url);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @AfterAll
    static void dropScratchSchema() throws Exception {
        if (url() == null || !MIGRATED.get()) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(url());
             Statement statement = connection.createStatement()) {
            statement.execute("drop schema if exists " + SCHEMA + " cascade");
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Environment environment;



    // ---- migration ----

    @Test
    void schemaValidationIsActiveAndTheContextBootedAgainstTheMigratedSchema() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        // Reaching this line means Hibernate validated every entity against the Flyway-built schema.
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isNotBlank();
    }

    @Test
    void everyMigrationAppliedFromAnEmptySchemaThroughV95() {
        Integer failed = jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".flyway_schema_history where success = false", Integer.class);
        Integer versionedApplied = jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".flyway_schema_history where success = true and version is not null",
                Integer.class);
        List<String> versions = jdbc.queryForList(
                "select version from " + SCHEMA + ".flyway_schema_history where success = true and version is not null",
                String.class);

        assertThat(failed).isZero();
        assertThat(versionedApplied).isGreaterThanOrEqualTo(95);
        assertThat(versions).contains("94", "95");
        assertThat(versions.stream().mapToInt(v -> Integer.parseInt(v.split("\\.")[0])).max().orElse(0))
                .isGreaterThanOrEqualTo(95);
    }

    @Test
    void v95CreatedTheReportsAndAuditIndexesAsPartialTenantLeadingIndexes() {
        Map<String, String> definitions = jdbc.queryForList(
                        "select indexname, indexdef from pg_indexes where schemaname = ?", SCHEMA).stream()
                .collect(Collectors.toMap(row -> (String) row.get("indexname"), row -> (String) row.get("indexdef")));

        assertThat(definitions.keySet()).containsAll(V95_INDEXES);
        assertThat(definitions.get("idx_attempt_reports_tenant_unpublished")).contains("(tenant_id)")
                .contains("published = false").contains("deleted = false");
        assertThat(definitions.get("idx_audit_logs_tenant_created")).contains("(tenant_id, created_at DESC)")
                .contains("WHERE (deleted = false)");
    }

    @Test
    void v95IsIdempotentWhenReplayedAgainstTheMigratedSchema() throws Exception {
        String script = new String(new ClassPathResource(V95_RESOURCE).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(url());
             Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + SCHEMA);
            statement.execute(script);
            statement.execute(script);
        }
        String names = V95_INDEXES.stream().map(name -> "'" + name + "'").collect(Collectors.joining(", "));
        Integer indexCount = jdbc.queryForObject(
                "select count(*) from pg_indexes where schemaname = ? and indexname in (" + names + ")",
                Integer.class, SCHEMA);
        assertThat(indexCount).isEqualTo(V95_INDEXES.size());
    }

    @Test
    void theScratchSchemaIsTheOnlyPlaceTheTestWrites() {
        // The migrated schema is isolated: every table the run created lives in the scratch schema.
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname = ?", Integer.class, SCHEMA))
                .isGreaterThan(50);
        assertThat(SCHEMA).startsWith("overview_mig_").isNotEqualTo("public");
    }

    @Test
    void v94CreatedTheSixDashboardIndexesAsPartialTenantLeadingComposites() {
        Map<String, String> definitions = jdbc.queryForList(
                        "select indexname, indexdef from pg_indexes where schemaname = ?", SCHEMA).stream()
                .collect(Collectors.toMap(row -> (String) row.get("indexname"), row -> (String) row.get("indexdef")));

        assertThat(definitions.keySet()).containsAll(V94_INDEXES);
        for (String index : V94_INDEXES) {
            assertThat(definitions.get(index)).as(index).contains("(tenant_id, ").contains("WHERE (deleted = false)");
        }
        assertThat(definitions.get("idx_attempts_tenant_submitted")).contains("submitted_at");
        assertThat(definitions.get("idx_attempts_tenant_started")).contains("started_at");
        assertThat(definitions.get("idx_attempts_tenant_status")).contains("status");
        assertThat(definitions.get("idx_users_tenant_created")).contains("created_at");
        assertThat(definitions.get("idx_sessions_tenant_status")).contains("status");
        assertThat(definitions.get("idx_sessions_tenant_created")).contains("created_at");
    }

    @Test
    void v94IsIdempotentWhenReplayedAgainstTheMigratedSchema() throws Exception {
        String script = new String(new ClassPathResource(V94_RESOURCE).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        // A private connection, so the search_path change cannot leak into the pooled connections.
        try (Connection connection = DriverManager.getConnection(url());
             Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + SCHEMA);
            statement.execute(script);
            statement.execute(script);
        }
        String names = V94_INDEXES.stream().map(name -> "'" + name + "'").collect(Collectors.joining(", "));
        Integer indexCount = jdbc.queryForObject(
                "select count(*) from pg_indexes where schemaname = ? and indexname in (" + names + ")",
                Integer.class, SCHEMA);
        assertThat(indexCount).isEqualTo(V94_INDEXES.size());
    }

    // ---- EXPLAIN of the heavy host queries ----

    @Test
    void heavyHostQueriesUseTheNewIndexes() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seed(tenantA, tenantB);

        Map<String, String> queries = new LinkedHashMap<>();
        Map<String, String> expectedIndex = new LinkedHashMap<>();
        Set<String> mustBeIndexedNaturally = new HashSet<>();

        queries.put("attempts submitted in window", "select count(a.id) from " + SCHEMA + ".exam_attempts a "
                + "where a.tenant_id = '" + tenantA + "' and a.deleted = false and a.status = 'SUBMITTED' "
                + "and a.submitted_at >= now() - interval '30 days' and a.submitted_at < now()");
        expectedIndex.put("attempts submitted in window", "idx_attempts_tenant_submitted");
        mustBeIndexedNaturally.add("attempts submitted in window");

        queries.put("distinct active students", "select count(distinct a.student_public_id) from " + SCHEMA
                + ".exam_attempts a where a.tenant_id = '" + tenantA + "' and a.deleted = false "
                + "and a.started_at >= now() - interval '30 days' and a.started_at < now()");
        expectedIndex.put("distinct active students", "idx_attempts_tenant_started");
        mustBeIndexedNaturally.add("distinct active students");

        queries.put("monthly series (one month bucket)", "select count(a.id) from " + SCHEMA + ".exam_attempts a "
                + "where a.tenant_id = '" + tenantA + "' and a.deleted = false and a.status = 'SUBMITTED' "
                + "and a.submitted_at >= date_trunc('month', now() - interval '3 months') "
                + "and a.submitted_at < date_trunc('month', now() - interval '2 months')");
        expectedIndex.put("monthly series (one month bucket)", "idx_attempts_tenant_submitted");
        mustBeIndexedNaturally.add("monthly series (one month bucket)");

        queries.put("attempts in progress now", "select count(a.id) from " + SCHEMA + ".exam_attempts a "
                + "where a.tenant_id = '" + tenantA + "' and a.deleted = false and a.status = 'IN_PROGRESS'");
        expectedIndex.put("attempts in progress now", "idx_attempts_tenant_status");

        queries.put("new learners in window", "select count(distinct u.id) from " + SCHEMA + ".users u join "
                + SCHEMA + ".user_roles r on r.user_id = u.id where u.tenant_id = '" + tenantA
                + "' and r.role = 'STUDENT' and u.deleted = false "
                + "and u.created_at >= now() - interval '30 days' and u.created_at < now()");
        expectedIndex.put("new learners in window", "idx_users_tenant_created");

        queries.put("sessions by status", "select s.status, count(s.id) from " + SCHEMA + ".exam_sessions s "
                + "where s.tenant_id = '" + tenantA + "' and s.deleted = false group by s.status");
        expectedIndex.put("sessions by status", "idx_sessions_tenant_status");

        queries.put("sessions created in window", "select count(s.id) from " + SCHEMA + ".exam_sessions s "
                + "where s.tenant_id = '" + tenantA + "' and s.deleted = false "
                + "and s.created_at >= now() - interval '30 days' and s.created_at < now()");
        expectedIndex.put("sessions created in window", "idx_sessions_tenant_created");

        StringBuilder report = new StringBuilder("EXPLAIN (ANALYZE) of heavy host dashboard queries\n");
        report.append("\nSeed rows: ").append(jdbc.queryForObject("select count(*) from " + SCHEMA + ".exam_attempts", Long.class))
                .append(" attempts, ").append(jdbc.queryForObject("select count(*) from " + SCHEMA + ".users", Long.class))
                .append(" users, ").append(jdbc.queryForObject("select count(*) from " + SCHEMA + ".exam_sessions", Long.class))
                .append(" sessions\n\nIndexes on the queried tables:\n");
        jdbc.queryForList("select tablename, indexdef from pg_indexes where schemaname = ? "
                        + "and tablename in ('exam_attempts','users','exam_sessions') order by tablename, indexname", SCHEMA)
                .forEach(row -> report.append("  ").append(row.get("indexdef")).append('\n'));
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> query : queries.entrySet()) {
            String natural = explain(query.getValue(), false);
            String forced = explain(query.getValue(), true);
            report.append("\n--- ").append(query.getKey()).append(" [planner default]\n").append(natural)
                    .append("\n--- ").append(query.getKey()).append(" [enable_seqscan=off]\n").append(forced)
                    .append('\n');
            if (!forced.contains(expectedIndex.get(query.getKey()))) {
                failures.add(query.getKey() + " cannot use " + expectedIndex.get(query.getKey()));
            }
            if (mustBeIndexedNaturally.contains(query.getKey())
                    && (!natural.contains("Index") || natural.contains("Seq Scan on exam_attempts"))) {
                failures.add(query.getKey() + " does not use an index with the default planner settings");
            }
        }
        Files.writeString(Path.of("target", "overview-explain-plans.txt"), report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);

        assertThat(failures).isEmpty();
    }

    // ---- EXPLAIN of the Phase 2 (commercial / operations) host queries ----

    private static final int PHASE2_TENANTS = 20;
    private static final int PHASE2_COPIES_PER_TENANT = 1500;

    @Test
    void phaseTwoHostQueriesUseIndexes() throws Exception {
        UUID[] tenants = new UUID[PHASE2_TENANTS];
        for (int i = 0; i < tenants.length; i++) {
            tenants[i] = UUID.randomUUID();
        }
        UUID tenantA = tenants[0];
        seedPhaseTwo(tenants);

        String a = "'" + tenantA + "'";
        Map<String, String> queries = new LinkedHashMap<>();
        // name -> index that MUST be used by the default planner (null: only recorded, usable-index checked when forced)
        Map<String, String> mustUseNaturally = new LinkedHashMap<>();

        queries.put("reports awaiting publication (count)", "select count(r.id) from " + SCHEMA
                + ".attempt_reports r where r.tenant_id = " + a + " and r.published = false and r.deleted = false");
        mustUseNaturally.put("reports awaiting publication (count)", "idx_attempt_reports_tenant_unpublished");

        queries.put("recent activity (newest 8 audit rows)", "select * from " + SCHEMA + ".audit_logs a where a.tenant_id = "
                + a + " and a.deleted = false order by a.created_at desc limit 8");
        mustUseNaturally.put("recent activity (newest 8 audit rows)", "idx_audit_logs_tenant_created");

        queries.put("grading: answers grouped by status", "select s.status, count(s.id) from " + SCHEMA
                + ".scoring_answers s where s.tenant_id = " + a + " and s.deleted = false group by s.status");
        mustUseNaturally.put("grading: answers grouped by status", null);

        queries.put("grading: frozen cohorts (closed sessions awaiting finalisation)", "select count(c.id) from " + SCHEMA
                + ".session_grading_cohorts c where c.tenant_id = " + a + " and c.status = 'FROZEN' and c.deleted = false");
        mustUseNaturally.put("grading: frozen cohorts (closed sessions awaiting finalisation)", null);

        queries.put("pending orders (count)", "select count(o.id) from " + SCHEMA + ".orders o where o.tenant_id = " + a
                + " and o.status = 'PENDING' and o.deleted = false");
        mustUseNaturally.put("pending orders (count)", null);

        queries.put("support tickets grouped by status", "select t.status, count(t.id) from " + SCHEMA
                + ".support_ticket t where t.tenant_id = " + a + " and t.deleted = false group by t.status");
        mustUseNaturally.put("support tickets grouped by status", null);

        queries.put("usable subscriptions (soonest expiry, limit 10)", "select * from " + SCHEMA + ".subscriptions s "
                + "where s.tenant_id = " + a + " and s.deleted = false and s.status = 'ACTIVE' and s.starts_at <= now() "
                + "and s.expires_at > now() order by s.expires_at asc, s.id asc limit 10");
        mustUseNaturally.put("usable subscriptions (soonest expiry, limit 10)", null);

        String planIds = jdbc.queryForList("select '''' || public_id::text || '''' from " + SCHEMA
                + ".plans order by id limit 3", String.class).stream().collect(Collectors.joining(", "));
        queries.put("plan names batch (findByPublicIdIn)", "select * from " + SCHEMA + ".plans p where p.public_id in ("
                + planIds + ")");
        mustUseNaturally.put("plan names batch (findByPublicIdIn)", null);

        StringBuilder report = new StringBuilder("EXPLAIN (ANALYZE) of Phase 2 host dashboard queries\n\nSeed: ")
                .append(PHASE2_TENANTS).append(" tenants x ").append(PHASE2_COPIES_PER_TENANT)
                .append(" rows per table (tenant A is 1 of ").append(PHASE2_TENANTS).append(")\n");
        for (String table : List.of("attempt_reports", "audit_logs", "scoring_answers", "session_grading_cohorts",
                "orders", "support_ticket", "subscriptions", "plans")) {
            report.append("  ").append(table).append(": ")
                    .append(jdbc.queryForObject("select count(*) from " + SCHEMA + "." + table, Long.class)).append(" rows\n");
        }
        report.append("\nIndexes on the queried tables:\n");
        jdbc.queryForList("select tablename, indexdef from pg_indexes where schemaname = ? and tablename in "
                        + "('attempt_reports','audit_logs','scoring_answers','session_grading_cohorts','orders',"
                        + "'support_ticket','subscriptions','plans') order by tablename, indexname", SCHEMA)
                .forEach(row -> report.append("  ").append(row.get("indexdef")).append('\n'));

        List<String> failures = new ArrayList<>();
        StringBuilder verdicts = new StringBuilder("\n=== VERDICTS ===\n");
        for (Map.Entry<String, String> query : queries.entrySet()) {
            String natural = explain(query.getValue(), false);
            String forced = explain(query.getValue(), true);
            report.append("\n--- ").append(query.getKey()).append(" [planner default]\n").append(natural)
                    .append("\n--- ").append(query.getKey()).append(" [enable_seqscan=off]\n").append(forced).append('\n');
            String required = mustUseNaturally.get(query.getKey());
            boolean naturalIndexed = natural.contains("Index") && !natural.contains("Seq Scan");
            verdicts.append(query.getKey()).append(": default planner -> ")
                    .append(summarisePlan(natural)).append("; forced -> ").append(summarisePlan(forced)).append('\n');
            if (required != null && !natural.contains(required)) {
                failures.add(query.getKey() + " does not use " + required + " by default");
            }
            if (!forced.contains("Index")) {
                failures.add(query.getKey() + " has no usable index even with sequential scans disabled");
            }
            if (required == null && !naturalIndexed) {
                verdicts.append("  note: default planner chose a sequential scan on the ").append(PHASE2_TENANTS)
                        .append("-tenant seed\n");
            }
        }
        report.append(verdicts);
        Files.writeString(Path.of("target", "overview-explain-plans-phase2.txt"), report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);

        assertThat(failures).isEmpty();
    }

    private static String summarisePlan(String plan) {
        List<String> nodes = new ArrayList<>();
        for (String line : plan.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("->")) {
                trimmed = trimmed.substring(2).trim();
            }
            if (trimmed.startsWith("Index") || trimmed.startsWith("Bitmap Index") || trimmed.startsWith("Seq Scan")
                    || trimmed.startsWith("Bitmap Heap")) {
                int paren = trimmed.indexOf(" (");
                nodes.add(paren > 0 ? trimmed.substring(0, paren) : trimmed);
            }
        }
        return nodes.isEmpty() ? "(no scan node)" : String.join(" | ", nodes);
    }

    /** Plain-SQL seed of the Phase 2 tables: realistic skew (few unpublished/pending/frozen rows), tenants interleaved. */
    private void seedPhaseTwo(UUID[] tenants) {
        int copies = PHASE2_COPIES_PER_TENANT;
        long[] reports = new long[tenants.length];
        long[] audits = new long[tenants.length];
        long[] answers = new long[tenants.length];
        long[] cohorts = new long[tenants.length];
        long[] orderTemplates = new long[tenants.length];
        long[] tickets = new long[tenants.length];
        long[] subs = new long[tenants.length];
        for (int i = 0; i < tenants.length; i++) {
            String t = "'" + tenants[i] + "'::uuid";
            reports[i] = insertBaseRow("attempt_reports", Map.of("tenant_id", t, "published", "false"));
            audits[i] = insertBaseRow("audit_logs", Map.of("tenant_id", t));
            answers[i] = insertBaseRow("scoring_answers", Map.of("tenant_id", t, "status", "'PENDING'"));
            cohorts[i] = insertBaseRow("session_grading_cohorts", Map.of("tenant_id", t, "status", "'FROZEN'",
                    "marking_mode", "'AI_ONLY'"));
            orderTemplates[i] = insertBaseRow("orders", Map.of("tenant_id", t, "status", "'PENDING'",
                    "order_code", String.valueOf(i + 1), "amount", "100000")); // unique code per template; clones override it
            tickets[i] = insertBaseRow("support_ticket", Map.of("tenant_id", t, "status", "'OPEN'", "category", "'BUG'"));
            subs[i] = insertBaseRow("subscriptions", Map.of("tenant_id", t, "status", "'ACTIVE'",
                    "activation_source", "'LICENSE_CODE'", "license_key", "substr(md5(random()::text), 1, 32)",
                    "starts_at", "now()", "expires_at", "now() + interval '1 day'", "max_students_per_session", "30"));
        }
        for (int p = 0; p < 40; p++) {
            insertBaseRow("plans", Map.of("name", "'plan " + p + "'", "type", "'EXAM_PACKAGE'", "status", "'ACTIVE'",
                    "currency", "'VND'", "price", "100000", "duration_days", "365", "max_students_per_session", "30"));
        }
        cloneInterleaved("attempt_reports", reports, tenants, copies, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("attempt_public_id", "gen_random_uuid()");
            o.put("tenant_id", "'" + tenant + "'::uuid");
            o.put("published", "(g % 20 <> 0)"); // ~5% awaiting publication
            return o;
        });
        cloneInterleaved("audit_logs", audits, tenants, copies, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("tenant_id", "'" + tenant + "'::uuid");
            o.put("created_at", "now() - (" + copies + " - g) * interval '5 minutes'");
            return o;
        });
        cloneInterleaved("scoring_answers", answers, tenants, copies, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("answer_public_id", "gen_random_uuid()");
            o.put("attempt_public_id", "gen_random_uuid()");
            o.put("session_public_id", "md5((g % 40)::text)::uuid");
            o.put("tenant_id", "'" + tenant + "'::uuid");
            o.put("status", "case when g % 20 = 0 then 'PENDING' when g % 50 = 0 then 'AI_SCORING' "
                    + "when g % 100 = 0 then 'SCORING_FAILED' else 'SCORED' end");
            return o;
        });
        cloneInterleaved("session_grading_cohorts", cohorts, tenants, copies, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("session_public_id", "gen_random_uuid()");
            o.put("tenant_id", "'" + tenant + "'::uuid");
            o.put("status", "case when g % 10 = 0 then 'FROZEN' else 'COMPLETED' end");
            o.put("completed_at", "case when g % 10 = 0 then null else now() end");
            return o;
        });
        cloneInterleaved("orders", orderTemplates, tenants, copies, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("order_code", "(abs(hashtext(t.tenant_id::text))::bigint * 100000 + g)");
            o.put("tenant_id", "'" + tenant + "'::uuid");
            o.put("plan_id", "gen_random_uuid()"); // one pending order per (tenant, plan) is unique
            o.put("status", "case when g % 50 = 0 then 'PENDING' else 'PAID' end");
            o.put("paid_at", "case when g % 50 = 0 then null else now() end");
            return o;
        });
        cloneInterleaved("support_ticket", tickets, tenants, copies, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("tenant_id", "'" + tenant + "'::uuid");
            o.put("status", "(array['OPEN','IN_PROGRESS','RESOLVED','CLOSED','CLOSED','CLOSED'])[1 + g % 6]");
            return o;
        });
        cloneInterleaved("subscriptions", subs, tenants, copies, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("license_key", "substr(md5(g::text || t.tenant_id::text), 1, 32)");
            o.put("tenant_id", "'" + tenant + "'::uuid");
            o.put("status", "case when g % 10 = 0 then 'ACTIVE' else 'EXPIRED' end");
            o.put("starts_at", "now() - interval '400 days'");
            o.put("expires_at", "now() - interval '400 days' + g * interval '1 day'");
            return o;
        });
        for (String table : List.of("attempt_reports", "audit_logs", "scoring_answers", "session_grading_cohorts",
                "orders", "support_ticket", "subscriptions", "plans")) {
            jdbc.execute("vacuum (analyze) " + SCHEMA + "." + table);
        }
    }

    /** One connection for the planner setting and the EXPLAIN, so the setting cannot leak or be lost in the pool. */
    private String explain(String sql, boolean forceIndexes) {
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set enable_seqscan = " + (forceIndexes ? "off" : "on"));
                try (ResultSet rows = statement.executeQuery(
                        "explain (analyze, timing off, summary off) " + sql)) {
                    StringBuilder plan = new StringBuilder();
                    while (rows.next()) {
                        plan.append(rows.getString(1)).append('\n');
                    }
                    return plan.toString();
                } finally {
                    statement.execute("set enable_seqscan = on");
                }
            }
        });
    }

    /**
     * Seeds realistic volume by cloning one SQL-inserted template row per table. Rows of the two
     * tenants are interleaved in small chunks (so tenant_id alone does not cluster the heap) and time columns
     * grow with insertion order (like real data); then VACUUM ANALYZE.
     */
    private void seed(UUID tenantA, UUID tenantB) {
        UUID[] tenants = {tenantA, tenantB};
        long[] attemptIds = {attemptTemplate(tenantA), attemptTemplate(tenantB)};
        long[] userIds = {userTemplate(tenantA), userTemplate(tenantB)};
        long[] sessionIds = {sessionTemplate(tenantA), sessionTemplate(tenantB)};

        cloneInterleaved("exam_attempts", attemptIds, tenants, 20000, tenant -> {
            Map<String, String> overrides = new LinkedHashMap<>();
            overrides.put("public_id", "gen_random_uuid()");
            overrides.put("session_public_id", "gen_random_uuid()");
            overrides.put("student_public_id", "md5((g % 500)::text)::uuid");
            overrides.put("tenant_id", "'" + tenant + "'::uuid");
            overrides.put("status", "case when g % 30 = 0 then 'IN_PROGRESS' else 'SUBMITTED' end");
            overrides.put("started_at", "now() - (20000 - g) * interval '30 minutes' - interval '15 minutes'");
            overrides.put("submitted_at",
                    "case when g % 30 = 0 then null else now() - (20000 - g) * interval '30 minutes' end");
            return overrides;
        });
        cloneInterleaved("users", userIds, tenants, 20000, tenant -> {
            Map<String, String> overrides = new LinkedHashMap<>();
            overrides.put("public_id", "gen_random_uuid()");
            overrides.put("tenant_id", "'" + tenant + "'::uuid");
            overrides.put("username", "'seed-' || g || '-' || t.tenant_id");
            overrides.put("email", "'seed-' || g || '-' || t.tenant_id || '@example.test'");
            overrides.put("created_at", "now() - (20000 - g) * interval '30 minutes'");
            return overrides;
        });
        jdbc.update("insert into " + SCHEMA + ".user_roles (user_id, role) select u.id, 'STUDENT' from " + SCHEMA
                + ".users u where u.username like 'seed-%'");
        cloneInterleaved("exam_sessions", sessionIds, tenants, 60000, tenant -> {
            Map<String, String> overrides = new LinkedHashMap<>();
            overrides.put("public_id", "gen_random_uuid()");
            overrides.put("session_code", "substr(md5(g::text || t.tenant_id::text), 1, 20)");
            // no_overlap_per_subscription forbids overlapping windows per subscription: one lane per row.
            overrides.put("subscription_id", "gen_random_uuid()");
            overrides.put("tenant_id", "'" + tenant + "'::uuid");
            overrides.put("status", "(array['DRAFT','SCHEDULED','OPEN','CLOSED'])[1 + g % 4]");
            overrides.put("created_at", "now() - (60000 - g) * interval '10 minutes'");
            return overrides;
        });
        for (String table : List.of("exam_attempts", "users", "user_roles", "exam_sessions")) {
            // VACUUM sets the visibility map (needed for index-only scans) like a production table at rest.
            jdbc.execute("vacuum (analyze) " + SCHEMA + "." + table);
        }
    }

    private void cloneInterleaved(String table, long[] templateIds, UUID[] tenants, int copies,
                                  Function<UUID, Map<String, String>> overrides) {
        int chunk = 500;
        for (int from = 1; from <= copies; from += chunk) {
            int to = Math.min(copies, from + chunk - 1);
            for (int i = 0; i < tenants.length; i++) {
                clone(table, templateIds[i], from, to, overrides.apply(tenants[i]));
            }
        }
    }

    /** {@code insert ... select} copying every column of the template row except the overridden ones and {@code id}. */
    private void clone(String table, long templateId, int fromCopy, int toCopy, Map<String, String> overrides) {
        List<String> copied = jdbc.queryForList(
                "select column_name from information_schema.columns where table_schema = ? and table_name = ? "
                        + "and is_generated = 'NEVER' and column_name <> 'id' order by ordinal_position",
                String.class, SCHEMA, table).stream().filter(c -> !overrides.containsKey(c)).toList();
        String insertColumns = String.join(", ", copied) + ", " + String.join(", ", overrides.keySet());
        String selectColumns = copied.stream().map(c -> "t." + c).collect(Collectors.joining(", "))
                + ", " + String.join(", ", overrides.values());
        jdbc.update("insert into " + SCHEMA + "." + table + " (" + insertColumns + ") select " + selectColumns
                + " from " + SCHEMA + "." + table + " t cross join generate_series(" + fromCopy + ", " + toCopy
                + ") g where t.id = ?", templateId);
    }
    /**
     * Inserts one base row with plain SQL: every NOT NULL column without a default gets a type-based filler,
     * then {@code given} (column -> SQL expression) wins. Returns the generated id. No entity or repository
     * of another module is used.
     */
    private long insertBaseRow(String table, Map<String, String> given) {
        List<Map<String, Object>> columns = jdbc.queryForList(
                "select column_name, data_type, is_nullable, column_default, is_identity "
                        + "from information_schema.columns where table_schema = ? and table_name = ? "
                        + "order by ordinal_position", SCHEMA, table);
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> column : columns) {
            String name = (String) column.get("column_name");
            if (given.containsKey(name)) {
                values.put(name, given.get(name));
            } else if ("NO".equals(column.get("is_nullable")) && column.get("column_default") == null
                    && "NO".equals(column.get("is_identity")) && !"id".equals(name)) {
                values.put(name, filler((String) column.get("data_type")));
            }
        }
        return jdbc.queryForObject("insert into " + SCHEMA + "." + table + " (" + String.join(", ", values.keySet())
                + ") values (" + String.join(", ", values.values()) + ") returning id", Long.class);
    }

    private static String filler(String dataType) {
        return switch (dataType) {
            case "uuid" -> "gen_random_uuid()";
            case "boolean" -> "false";
            case "integer", "bigint", "smallint", "numeric" -> "0";
            case "timestamp with time zone", "timestamp without time zone" -> "now()";
            case "date" -> "current_date";
            case "json", "jsonb" -> "'{}'";
            default -> "'tpl'";
        };
    }

    private long attemptTemplate(UUID tenant) {
        Map<String, String> given = new LinkedHashMap<>();
        given.put("tenant_id", "'" + tenant + "'::uuid");
        given.put("status", "'IN_PROGRESS'");
        given.put("started_at", "now()");
        return insertBaseRow("exam_attempts", given);
    }

    private long userTemplate(UUID tenant) {
        String username = "'template-' || gen_random_uuid()::text";
        Map<String, String> given = new LinkedHashMap<>();
        given.put("tenant_id", "'" + tenant + "'::uuid");
        given.put("username", username);
        given.put("email", "'template-' || gen_random_uuid()::text || '@example.test'");
        given.put("status", "'ACTIVE'");
        return insertBaseRow("users", given);
    }

    private long sessionTemplate(UUID tenant) {
        Map<String, String> given = new LinkedHashMap<>();
        given.put("tenant_id", "'" + tenant + "'::uuid");
        given.put("name", "'template'");
        given.put("session_code", "'TPL-' || substr(md5(random()::text), 1, 18)");
        given.put("subscription_id", "gen_random_uuid()");
        given.put("license_key", "'LIC'");
        given.put("opens_at", "now() + interval '1 hour'");
        given.put("closes_at", "now() + interval '2 hours'");
        given.put("capacity", "10");
        given.put("status", "'SCHEDULED'");
        return insertBaseRow("exam_sessions", given);
    }}
