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
import java.util.Arrays;
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
 * Opt-in PostgreSQL proof for the overview migrations (V94, V95, V96, V97), enabled by passing
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

    private static final String V96_RESOURCE = "db/migration/V96__overview_admin_dashboard_indexes.sql";

    private static final Set<String> V95_INDEXES = Set.of(
            "idx_attempt_reports_tenant_unpublished", "idx_audit_logs_tenant_created");

    /** index -> {table, key columns} of the six platform-wide (no tenant prefix) Phase 3 indexes. */
    private static final Map<String, String[]> V96_INDEX_SHAPES = Map.of(
            "idx_attempts_started", new String[]{"exam_attempts", "(started_at)"},
            "idx_attempts_submitted", new String[]{"exam_attempts", "(submitted_at)"},
            "idx_subscriptions_status_expires", new String[]{"subscriptions", "(status, expires_at)"},
            "idx_support_ticket_status_created", new String[]{"support_ticket", "(status, created_at)"},
            "idx_tenant_applications_status_created", new String[]{"tenant_applications", "(status, created_at)"},
            "idx_tenants_created", new String[]{"tenants", "(created_at)"});
    private static final Set<String> V96_INDEXES = V96_INDEX_SHAPES.keySet();

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
    void everyMigrationAppliedFromAnEmptySchemaThroughV97() {
        Integer failed = jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".flyway_schema_history where success = false", Integer.class);
        Integer versionedApplied = jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".flyway_schema_history where success = true and version is not null",
                Integer.class);
        List<String> versions = jdbc.queryForList(
                "select version from " + SCHEMA + ".flyway_schema_history where success = true and version is not null",
                String.class);

        assertThat(failed).isZero();
        assertThat(versionedApplied).isGreaterThanOrEqualTo(97);
        assertThat(versions).contains("94", "95", "96", "97");
        assertThat(versions.stream().mapToInt(v -> Integer.parseInt(v.split("\\.")[0])).max().orElse(0))
                .isGreaterThanOrEqualTo(97);
    }

    @Test
    void v96CreatedTheSixPlatformWideIndexesAsPartialDefinitionsOnTheRightTables() {
        Map<String, String> definitions = jdbc.queryForList(
                        "select indexname, indexdef from pg_indexes where schemaname = ?", SCHEMA).stream()
                .collect(Collectors.toMap(row -> (String) row.get("indexname"), row -> (String) row.get("indexdef")));

        assertThat(V96_INDEXES).hasSize(6);
        assertThat(definitions.keySet()).containsAll(V96_INDEXES);
        V96_INDEX_SHAPES.forEach((index, shape) -> {
            String definition = definitions.get(index);
            assertThat(definition).as(index).contains(" ON " + SCHEMA + "." + shape[0] + " USING btree " + shape[1])
                    .contains("WHERE (deleted = false)");
            // platform-wide queries: no tenant prefix on any of the six
            assertThat(definition).as(index).doesNotContain("tenant_id");
        });
    }

    @Test
    void v96IsIdempotentWhenReplayedAgainstTheMigratedSchema() throws Exception {
        String script = new String(new ClassPathResource(V96_RESOURCE).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        String before = definitionsOf(V96_INDEXES);
        try (Connection connection = DriverManager.getConnection(url());
             Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + SCHEMA);
            statement.execute(script);
            statement.execute(script);
        }
        Integer indexCount = jdbc.queryForObject("select count(*) from pg_indexes where schemaname = ? and indexname in ("
                + V96_INDEXES.stream().map(name -> "'" + name + "'").collect(Collectors.joining(", ")) + ")",
                Integer.class, SCHEMA);

        assertThat(indexCount).isEqualTo(V96_INDEXES.size());
        assertThat(definitionsOf(V96_INDEXES)).as("definitions unchanged by the replay").isEqualTo(before);
    }

    private String definitionsOf(Set<String> indexes) {
        return jdbc.queryForList("select indexname || ' => ' || indexdef from pg_indexes where schemaname = ? "
                        + "and indexname in (" + indexes.stream().map(name -> "'" + name + "'")
                        .collect(Collectors.joining(", ")) + ") order by indexname", String.class, SCHEMA)
                .stream().collect(Collectors.joining("\n"));
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
        expectedIndex.put("sessions by status", "idx_sessions_tenant_status|idx_sessions_tenant_window");

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
            String[] acceptableIndexes = expectedIndex.get(query.getKey()).split("\\|");
            if (Arrays.stream(acceptableIndexes).noneMatch(forced::contains)) {
                failures.add(query.getKey() + " cannot use one of " + String.join(", ", acceptableIndexes));
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

    // ---- EXPLAIN of the Phase 3 (platform-wide admin) queries ----

    private static final int P3_TENANTS = 30;
    private static final int P3_PER_TENANT = 2000;
    private static final int P3_TENANT_ROWS = 4000;
    private static final int P3_APPLICATIONS = 12000;
    private static final int P3_LICENSES = 30000;

    /** One platform-wide query: its SQL, the index that must be USABLE (forced plan) and whether the default planner must pick an index. */
    private record PlatformQuery(String name, String sql, String usableIndex, boolean indexedByDefault) {
    }

    @Test
    void phaseThreePlatformWideQueriesAndTheirPlans() throws Exception {
        UUID[] tenants = new UUID[P3_TENANTS];
        for (int i = 0; i < tenants.length; i++) {
            tenants[i] = UUID.randomUUID();
        }
        seedPhaseThree(tenants);

        String s = SCHEMA + ".";
        String last30 = "now() - interval '30 days'";
        List<PlatformQuery> queries = List.of(
                new PlatformQuery("platform distinct active students (started_at, current 30d)",
                        "select count(distinct a.student_public_id) from " + s + "exam_attempts a where a.deleted = false "
                                + "and a.started_at >= " + last30 + " and a.started_at < now()",
                        "idx_attempts_started", true),
                new PlatformQuery("platform distinct active students (started_at, previous 30d)",
                        "select count(distinct a.student_public_id) from " + s + "exam_attempts a where a.deleted = false "
                                + "and a.started_at >= now() - interval '60 days' and a.started_at < " + last30,
                        "idx_attempts_started", true),
                new PlatformQuery("platform submitted in window",
                        "select count(a.id) from " + s + "exam_attempts a where a.deleted = false and a.status = 'SUBMITTED' "
                                + "and a.submitted_at >= " + last30 + " and a.submitted_at < now()",
                        "idx_attempts_submitted", true),
                // the REAL query since PERF-001: UserRepository.countByRoleGroupedByTenant uses count(u.id)
                new PlatformQuery("students per tenant (users join user_roles, grouped by tenant, count(u.id) = production query)",
                        "select u.tenant_id, count(u.id) from " + s + "users u join " + s + "user_roles r "
                                + "on r.user_id = u.id where r.role = 'STUDENT' and u.deleted = false "
                                + "and u.tenant_id is not null group by u.tenant_id",
                        null, false),
                // comparison only: the query before PERF-001 was resolved (count(distinct) forces a sort that spills to disk)
                new PlatformQuery("students per tenant - count(distinct u.id) (PREVIOUS query, comparison only)",
                        "select u.tenant_id, count(distinct u.id) from " + s + "users u join " + s + "user_roles r "
                                + "on r.user_id = u.id where r.role = 'STUDENT' and u.deleted = false "
                                + "and u.tenant_id is not null group by u.tenant_id",
                        null, false),
                new PlatformQuery("subscriptions usable now (count)",
                        "select count(s.id) from " + s + "subscriptions s where s.deleted = false and s.status = 'ACTIVE' "
                                + "and s.starts_at <= now() and s.expires_at > now()",
                        "idx_subscriptions_status_expires", true),
                new PlatformQuery("subscriptions usable and expiring within 30 days (count)",
                        "select count(s.id) from " + s + "subscriptions s where s.deleted = false and s.status = 'ACTIVE' "
                                + "and s.starts_at <= now() and s.expires_at > now() and s.expires_at <= now() + interval '30 days'",
                        "idx_subscriptions_status_expires", true),
                new PlatformQuery("subscriptions activated in window by source (created_at range)",
                        "select s.activation_source, count(s.id) from " + s + "subscriptions s where s.deleted = false "
                                + "and s.created_at >= " + last30 + " and s.created_at < now() group by s.activation_source",
                        null, false),
                new PlatformQuery("support tickets grouped by status (platform-wide)",
                        "select t.status, count(t.id) from " + s + "support_ticket t where t.deleted = false group by t.status",
                        "idx_support_ticket_status_created", false),
                new PlatformQuery("support tickets opened in window (created_at range)",
                        "select count(t.id) from " + s + "support_ticket t where t.deleted = false "
                                + "and t.created_at >= " + last30 + " and t.created_at < now()",
                        null, false),
                new PlatformQuery("support oldest 5 OPEN tickets",
                        "select t.* from " + s + "support_ticket t where t.deleted = false and t.status = 'OPEN' "
                                + "order by t.created_at asc, t.id asc limit 5",
                        "idx_support_ticket_status_created", true),
                new PlatformQuery("applications pending (count)",
                        "select count(a.id) from " + s + "tenant_applications a where a.deleted = false and a.status = 'PENDING'",
                        "idx_tenant_applications_status_created|uq_application_code_active", true),
                new PlatformQuery("applications oldest 5 pending",
                        "select a.* from " + s + "tenant_applications a where a.deleted = false and a.status = 'PENDING' "
                                + "order by a.created_at asc, a.id asc limit 5",
                        "idx_tenant_applications_status_created", true),
                new PlatformQuery("applications received in window (created_at range)",
                        "select count(a.id) from " + s + "tenant_applications a where a.deleted = false "
                                + "and a.created_at >= " + last30 + " and a.created_at < now()",
                        null, false),
                new PlatformQuery("tenants created in window (count)",
                        "select count(t.id) from " + s + "tenants t where t.deleted = false and t.created_at >= " + last30
                                + " and t.created_at < now()",
                        "idx_tenants_created", true),
                new PlatformQuery("tenants growth series (one month bucket)",
                        "select count(t.id) from " + s + "tenants t where t.deleted = false "
                                + "and t.created_at >= date_trunc('month', now() - interval '3 months') "
                                + "and t.created_at < date_trunc('month', now() - interval '2 months')",
                        "idx_tenants_created", true),
                new PlatformQuery("recent tenants (newest 5)",
                        "select t.* from " + s + "tenants t where t.deleted = false order by t.created_at desc, t.id desc limit 5",
                        "idx_tenants_created", true),
                new PlatformQuery("tenants grouped by status",
                        "select t.status, count(t.id) from " + s + "tenants t where t.deleted = false group by t.status",
                        null, false),
                new PlatformQuery("license codes grouped by stored status",
                        "select c.status, count(c.id) from " + s + "license_codes c where c.deleted = false group by c.status",
                        null, false),
                new PlatformQuery("license codes ISSUED past expiry (derived expired)",
                        "select count(c.id) from " + s + "license_codes c where c.deleted = false and c.status = 'ISSUED' "
                                + "and c.code_expires_at is not null and c.code_expires_at <= now()",
                        "idx_license_codes_status_expiry", false),
                new PlatformQuery("license codes ISSUED expiring within 7 days",
                        "select count(c.id) from " + s + "license_codes c where c.deleted = false and c.status = 'ISSUED' "
                                + "and c.code_expires_at > now() and c.code_expires_at <= now() + interval '7 days'",
                        "idx_license_codes_status_expiry", false),
                new PlatformQuery("license codes redeemed in window",
                        "select count(c.id) from " + s + "license_codes c where c.deleted = false "
                                + "and c.redeemed_at >= " + last30 + " and c.redeemed_at < now()",
                        null, false),
                new PlatformQuery("plans grouped by status and by type",
                        "select p.status, p.type, count(p.id) from " + s + "plans p where p.deleted = false group by p.status, p.type",
                        null, false));

        StringBuilder report = new StringBuilder("EXPLAIN (ANALYZE) of Phase 3 platform-wide admin dashboard queries\n\nSeed (plus rows left by "
                + "the other tests of this class in the same scratch schema):\n");
        for (String table : List.of("exam_attempts", "users", "user_roles", "subscriptions", "support_ticket",
                "tenant_applications", "tenants", "license_codes", "plans")) {
            report.append("  ").append(table).append(": ")
                    .append(jdbc.queryForObject("select count(*) from " + SCHEMA + "." + table, Long.class)).append(" rows\n");
        }
        report.append("  attempts started in the last 30 days: ").append(jdbc.queryForObject("select count(*) from " + SCHEMA
                + ".exam_attempts where started_at >= now() - interval '30 days'", Long.class)).append('\n');
        report.append("  distinct tenants with students: ").append(jdbc.queryForObject("select count(distinct tenant_id) from "
                + SCHEMA + ".users", Long.class)).append('\n');
        report.append("\nIndexes on the queried tables:\n");
        jdbc.queryForList("select tablename, indexdef from pg_indexes where schemaname = ? and tablename in "
                        + "('exam_attempts','users','user_roles','subscriptions','support_ticket','tenant_applications',"
                        + "'tenants','license_codes','plans') order by tablename, indexname", SCHEMA)
                .forEach(row -> report.append("  ").append(row.get("indexdef")).append('\n'));

        List<String> failures = new ArrayList<>();
        StringBuilder verdicts = new StringBuilder("\n=== VERDICTS ===\n");
        for (PlatformQuery query : queries) {
            String natural = explain(query.sql(), false);
            String forced = explain(query.sql(), true);
            report.append("\n--- ").append(query.name()).append(" [planner default]\n").append(natural)
                    .append("\n--- ").append(query.name()).append(" [enable_seqscan=off]\n").append(forced).append('\n');
            boolean naturalSeqScan = natural.contains("Seq Scan");
            verdicts.append(query.name()).append(": default planner -> ").append(summarisePlan(natural))
                    .append("; forced -> ").append(summarisePlan(forced)).append('\n');
            // "a|b": either index satisfies the intent (e.g. the pre-existing partial unique index on PENDING rows)
            if (query.usableIndex() != null && Arrays.stream(query.usableIndex().split("\\|")).noneMatch(forced::contains)) {
                failures.add(query.name() + " cannot use " + query.usableIndex() + " even with sequential scans disabled");
            }
            if (query.indexedByDefault() && (!natural.contains("Index") || naturalSeqScan)) {
                failures.add(query.name() + " does not use an index with the default planner settings");
            }
            if (query.usableIndex() == null && naturalSeqScan) {
                verdicts.append("  note: default planner sequentially scans a table for this query (no covering index by design)\n");
            }
        }
        report.append(verdicts);
        Files.writeString(Path.of("target", "overview-explain-plans-phase3.txt"), report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);

        assertThat(failures).isEmpty();
    }

    /**
     * Plain-SQL seed of the platform-wide tables: 30 tenants interleaved in chunks, attempts/users/tickets/subscriptions
     * with time columns growing in insertion order, a skewed status mix like production, then VACUUM ANALYZE.
     * No entity or repository of another module is used.
     */
    private void seedPhaseThree(UUID[] tenants) {
        int copies = P3_PER_TENANT;
        long[] attempts = new long[tenants.length];
        long[] users = new long[tenants.length];
        long[] tickets = new long[tenants.length];
        long[] subs = new long[tenants.length];
        for (int i = 0; i < tenants.length; i++) {
            String t = "'" + tenants[i] + "'::uuid";
            attempts[i] = attemptTemplate(tenants[i]);
            users[i] = userTemplate(tenants[i]);
            tickets[i] = insertBaseRow("support_ticket", Map.of("tenant_id", t, "status", "'OPEN'", "category", "'BUG'"));
            subs[i] = insertBaseRow("subscriptions", Map.of("tenant_id", t, "status", "'ACTIVE'",
                    "activation_source", "'LICENSE_CODE'", "license_key", "substr(md5(random()::text), 1, 32)",
                    "starts_at", "now()", "expires_at", "now() + interval '1 day'", "max_students_per_session", "30"));
        }
        long tenantTemplate = insertBaseRow("tenants", Map.of("code", "'p3tpl'", "name", "'p3tpl'",
                "organization_type", "'SCHOOL'", "status", "'ACTIVE'", "package_name", "'starter'",
                "student_limit", "100"));
        long applicationTemplate = insertBaseRow("tenant_applications", Map.of("org_name", "'p3tpl'",
                "org_type", "'SCHOOL'", "requested_code", "'p3tpl'", "contact_email", "'p3tpl@example.test'",
                "status", "'PENDING'"));
        long licenseTemplate = insertBaseRow("license_codes", Map.of("code", "'P3TPL'", "status", "'ISSUED'"));
        for (int p = 0; p < 40; p++) {
            Map<String, String> plan = new LinkedHashMap<>();
            plan.put("name", "'p3 plan " + p + "'");
            plan.put("status", "(array['ACTIVE','DRAFT','ARCHIVED'])[1 + " + (p % 3) + "]");
            plan.put("currency", "'VND'");
            plan.put("price", "100000");
            if (p % 4 == 0) { // chk_plans_family_fields: capacity plans carry slots only
                plan.put("type", "'STUDENT_CAPACITY'");
                plan.put("extra_student_slots", "100");
            } else {
                plan.put("type", "'EXAM_PACKAGE'");
                plan.put("duration_days", "365");
                plan.put("max_students_per_session", "30");
            }
            insertBaseRow("plans", plan);
        }

        cloneInterleaved("exam_attempts", attempts, tenants, copies, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("session_public_id", "gen_random_uuid()");
            o.put("student_public_id", "md5((g % 400)::text || t.tenant_id::text)::uuid");
            o.put("tenant_id", "'" + tenant + "'::uuid");
            o.put("status", "case when g % 30 = 0 then 'IN_PROGRESS' else 'SUBMITTED' end");
            o.put("started_at", "now() - (" + copies + " - g) * interval '5 hours'");
            o.put("submitted_at", "case when g % 30 = 0 then null else now() - (" + copies
                    + " - g) * interval '5 hours' + interval '40 minutes' end");
            o.put("deleted", "(g % 97 = 0)");
            return o;
        });
        cloneInterleaved("users", users, tenants, copies, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("tenant_id", "'" + tenant + "'::uuid");
            o.put("username", "'p3seed-' || g || '-' || t.tenant_id");
            o.put("email", "'p3seed-' || g || '-' || t.tenant_id || '@example.test'");
            o.put("created_at", "now() - (" + copies + " - g) * interval '5 hours'");
            o.put("deleted", "(g % 33 = 0)");
            return o;
        });
        jdbc.update("insert into " + SCHEMA + ".user_roles (user_id, role) select u.id, 'STUDENT' from " + SCHEMA
                + ".users u where u.username like 'p3seed-%'");
        jdbc.update("insert into " + SCHEMA + ".user_roles (user_id, role) select u.id, 'HOST_ADMIN' from " + SCHEMA
                + ".users u where u.username like 'p3seed-%' and u.id % 50 = 0");
        cloneInterleaved("support_ticket", tickets, tenants, copies / 2, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("tenant_id", "'" + tenant + "'::uuid");
            o.put("status", "case when g % 20 = 0 then 'OPEN' when g % 20 = 1 then 'IN_PROGRESS' "
                    + "when g % 10 = 2 then 'RESOLVED' else 'CLOSED' end");
            o.put("created_at", "now() - (" + (copies / 2) + " - g) * interval '10 hours'");
            return o;
        });
        cloneInterleaved("subscriptions", subs, tenants, copies / 2, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("license_key", "substr(md5('p3' || g::text || t.tenant_id::text), 1, 32)");
            o.put("tenant_id", "'" + tenant + "'::uuid");
            o.put("status", "case when g % 10 = 0 then 'ACTIVE' when g % 25 = 1 then 'CANCELLED' else 'EXPIRED' end");
            o.put("activation_source", "case when g % 2 = 0 then 'PAYMENT' else 'LICENSE_CODE' end");
            o.put("starts_at", "now() - interval '400 days'");
            o.put("expires_at", "case when g % 10 = 0 then now() + ((g % 300) - 30) * interval '1 day' "
                    + "else now() - interval '400 days' + g * interval '1 day' end");
            o.put("created_at", "now() - (" + (copies / 2) + " - g) * interval '10 hours'");
            return o;
        });
        cloneInterleaved("tenants", new long[]{tenantTemplate}, new UUID[]{UUID.randomUUID()}, P3_TENANT_ROWS, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("code", "'p3-' || g");
            o.put("name", "'p3 tenant ' || g");
            o.put("status", "case when g % 15 = 0 then 'SUSPENDED' else 'ACTIVE' end");
            o.put("organization_type", "(array['SCHOOL','CENTER','UNIVERSITY'])[1 + g % 3]");
            o.put("created_at", "now() - (" + P3_TENANT_ROWS + " - g) * interval '5 hours'");
            o.put("deleted", "(g % 50 = 0)");
            return o;
        });
        cloneInterleaved("tenant_applications", new long[]{applicationTemplate}, new UUID[]{UUID.randomUUID()},
                P3_APPLICATIONS, tenant -> {
                    Map<String, String> o = new LinkedHashMap<>();
                    o.put("public_id", "gen_random_uuid()");
                    o.put("requested_code", "'rc-' || g");
                    o.put("status", "case when g % 33 = 0 then 'PENDING' when g % 6 = 0 then 'REJECTED' else 'APPROVED' end");
                    o.put("created_at", "now() - (" + P3_APPLICATIONS + " - g) * interval '1 hour'");
                    return o;
                });
        cloneInterleaved("license_codes", new long[]{licenseTemplate}, new UUID[]{UUID.randomUUID()}, P3_LICENSES,
                tenant -> {
                    Map<String, String> o = new LinkedHashMap<>();
                    o.put("public_id", "gen_random_uuid()");
                    o.put("code", "'P3-' || g");
                    o.put("plan_id", "gen_random_uuid()");
                    o.put("status", "case when g % 10 < 6 then 'ISSUED' when g % 10 < 9 then 'REDEEMED' "
                            + "when g % 20 = 9 then 'REVOKED' else 'EXPIRED' end");
                    o.put("code_expires_at", "case when g % 3 = 0 then null when g % 3 = 1 "
                            + "then now() + (g % 90) * interval '1 day' else now() - (g % 90) * interval '1 day' end");
                    o.put("redeemed_at", "case when g % 10 between 6 and 8 then now() - (g % 400) * interval '1 day' end");
                    o.put("redeemed_by_tenant_id", "case when g % 10 between 6 and 8 then gen_random_uuid() end");
                    o.put("issued_at", "now() - interval '500 days' + g * interval '1 hour'");
                    return o;
                });
        for (String table : List.of("exam_attempts", "users", "user_roles", "subscriptions", "support_ticket",
                "tenant_applications", "tenants", "license_codes", "plans")) {
            jdbc.execute("vacuum (analyze) " + SCHEMA + "." + table);
        }
    }

    // ---- Phase 4: V97 (orders paid_at index), revenue and question bank queries ----

    private static final String V97_RESOURCE = "db/migration/V97__overview_admin_revenue_index.sql";
    private static final String V97_INDEX = "idx_orders_paid_at";
    private static final int P4_ORDERS = 100_000;
    private static final int P4_QUESTIONS = 40_000;

    @Test
    void v97CreatedThePartialPaidAtIndexOnPaidAndNotDeletedOrdersOnly() {
        String definition = jdbc.queryForObject(
                "select indexdef from pg_indexes where schemaname = ? and indexname = ?", String.class, SCHEMA, V97_INDEX);

        assertThat(definition).contains(" ON " + SCHEMA + ".orders USING btree (paid_at)")
                .contains("deleted = false").contains("'PAID'").doesNotContain("tenant_id");
        // The pre-existing orders indexes lead with tenant or status and cannot serve a paid_at range on their own.
        List<String> others = jdbc.queryForList("select indexdef from pg_indexes where schemaname = ? "
                + "and tablename = 'orders' and indexname <> ?", String.class, SCHEMA, V97_INDEX);
        assertThat(others).noneMatch(def -> def.contains("(paid_at"));
    }

    @Test
    void v97IsIdempotentWhenReplayedAgainstTheMigratedSchema() throws Exception {
        String before = definitionsOf(Set.of(V97_INDEX));

        runMigrationScript(V97_RESOURCE);
        runMigrationScript(V97_RESOURCE);

        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where schemaname = ? and indexname = ?",
                Integer.class, SCHEMA, V97_INDEX)).isEqualTo(1);
        assertThat(definitionsOf(Set.of(V97_INDEX))).as("definition unchanged by the replay").isEqualTo(before)
                .isNotBlank();
    }

    private void runMigrationScript(String resource) throws Exception {
        String script = new String(new ClassPathResource(resource).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(url());
             Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + SCHEMA);
            statement.execute(script);
        }
    }

    /** PREPARE / EXECUTE on one connection: the first executions use custom plans, then (auto) possibly a generic one. */
    private String explainPrepared(String body, String paramTypes, String args, boolean forceGeneric) {
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set enable_seqscan = on");
                statement.execute("set plan_cache_mode = " + (forceGeneric ? "force_generic_plan" : "auto"));
                statement.execute("prepare p4q(" + paramTypes + ") as " + body);
                try {
                    for (int i = 0; i < 6; i++) { // more than the 5 custom plans PostgreSQL tries before comparing
                        statement.execute("execute p4q(" + args + ")");
                    }
                    try (ResultSet rows = statement.executeQuery(
                            "explain (analyze, timing off, summary on) execute p4q(" + args + ")")) {
                        StringBuilder plan = new StringBuilder();
                        while (rows.next()) {
                            plan.append(rows.getString(1)).append('\n');
                        }
                        return plan.toString();
                    }
                } finally {
                    statement.execute("deallocate p4q");
                    statement.execute("set plan_cache_mode = auto");
                }
            }
        });
    }

    /** Wall time of the whole revenue load of one cache miss (2 windows + 12 months) through bound parameters, in ms. */
    private double revenueLoadMillis(List<java.sql.Timestamp> boundaries, int rounds) {
        String sql = "select o.currency, sum(o.amount), count(o.id) from " + SCHEMA + ".orders o where o.deleted = false "
                + "and o.status = ? and o.paid_at >= ? and o.paid_at < ? group by o.currency";
        java.sql.Timestamp now = jdbc.queryForObject("select now()", java.sql.Timestamp.class);
        java.sql.Timestamp d30 = jdbc.queryForObject("select now() - interval '30 days'", java.sql.Timestamp.class);
        java.sql.Timestamp d60 = jdbc.queryForObject("select now() - interval '60 days'", java.sql.Timestamp.class);
        for (int warm = 0; warm < 3; warm++) {
            runRevenueLoad(sql, boundaries, now, d30, d60);
        }
        long start = System.nanoTime();
        for (int round = 0; round < rounds; round++) {
            runRevenueLoad(sql, boundaries, now, d30, d60);
        }
        return (System.nanoTime() - start) / 1_000_000.0 / rounds;
    }

    private void runRevenueLoad(String sql, List<java.sql.Timestamp> boundaries, java.sql.Timestamp now,
            java.sql.Timestamp d30, java.sql.Timestamp d60) {
        jdbc.queryForList(sql, "PAID", d30, now);
        jdbc.queryForList(sql, "PAID", d60, d30);
        for (int i = 0; i + 1 < boundaries.size(); i++) {
            jdbc.queryForList(sql, "PAID", boundaries.get(i), boundaries.get(i + 1));
        }
    }

    @Test
    void phaseFourRevenueAndQuestionBankQueriesAndTheirPlans() throws Exception {
        seedPhaseFour();
        String s = SCHEMA + ".";

        String revenueBody = "select o.currency, sum(o.amount), count(o.id) from " + s + "orders o where o.deleted = false "
                + "and o.status = $1 and o.paid_at >= $2 and o.paid_at < $3 group by o.currency";
        String revenueArgs = "'PAID', now() - interval '30 days', now()";
        String previousArgs = "'PAID', now() - interval '60 days', now() - interval '30 days'";
        String last30 = "now() - interval '30 days'";
        String vnMonthStart = "(date_trunc('month', timezone('Asia/Ho_Chi_Minh', now()))";
        record Q(String name, String sql, String mustUseIndexWhenForced) {
        }
        List<Q> queries = List.of(
                new Q("revenue: current 30d window per currency (literal PAID)",
                        "select o.currency, sum(o.amount), count(o.id) from " + s + "orders o where o.deleted = false "
                                + "and o.status = 'PAID' and o.paid_at >= " + last30 + " and o.paid_at < now() group by o.currency",
                        V97_INDEX),
                new Q("revenue: previous 30d window per currency (literal PAID)",
                        "select o.currency, sum(o.amount), count(o.id) from " + s + "orders o where o.deleted = false "
                                + "and o.status = 'PAID' and o.paid_at >= now() - interval '60 days' and o.paid_at < " + last30
                                + " group by o.currency",
                        V97_INDEX),
                new Q("revenue: monthly bucket - oldest of the 12 (Asia/Ho_Chi_Minh month)",
                        "select o.currency, sum(o.amount), count(o.id) from " + s + "orders o where o.deleted = false "
                                + "and o.status = 'PAID' and o.paid_at >= " + vnMonthStart + " - interval '11 months') at time zone 'Asia/Ho_Chi_Minh' "
                                + "and o.paid_at < " + vnMonthStart + " - interval '10 months') at time zone 'Asia/Ho_Chi_Minh' group by o.currency",
                        V97_INDEX),
                new Q("revenue: monthly bucket - current partial month",
                        "select o.currency, sum(o.amount), count(o.id) from " + s + "orders o where o.deleted = false "
                                + "and o.status = 'PAID' and o.paid_at >= " + vnMonthStart + ") at time zone 'Asia/Ho_Chi_Minh' "
                                + "and o.paid_at < " + vnMonthStart + " + interval '1 month') at time zone 'Asia/Ho_Chi_Minh' group by o.currency",
                        V97_INDEX),
                new Q("question bank: counts by status, section, pool (the new facade query)",
                        "select q.status, q.task_type_section, q.pool, count(q.id) from " + s + "questions q "
                                + "where q.deleted = false and q.visibility = 'SHARED' group by q.status, q.task_type_section, q.pool",
                        null),
                new Q("question bank: legacy stats by section",
                        "select q.task_type_section, count(q.id) from " + s + "questions q "
                                + "where q.deleted = false and q.visibility = 'SHARED' group by q.task_type_section",
                        null),
                new Q("question bank: legacy stats by status",
                        "select q.status, count(q.id) from " + s + "questions q "
                                + "where q.deleted = false and q.visibility = 'SHARED' group by q.status",
                        null),
                new Q("question bank: created in 30d window",
                        "select count(q.id) from " + s + "questions q where q.deleted = false and q.visibility = 'SHARED' "
                                + "and q.created_at >= " + last30 + " and q.created_at < now()",
                        null));

        StringBuilder report = new StringBuilder("EXPLAIN (ANALYZE) of Phase 4 queries: online revenue (orders) and question bank\n\n");
        for (String table : List.of("orders", "questions")) {
            report.append("  ").append(table).append(": ")
                    .append(jdbc.queryForObject("select count(*) from " + s + table, Long.class)).append(" rows\n");
        }
        report.append("  PAID and not deleted orders: ").append(jdbc.queryForObject("select count(*) from " + s
                + "orders where status = 'PAID' and deleted = false", Long.class)).append('\n');
        report.append("  PAID orders paid in the last 30 days: ").append(jdbc.queryForObject("select count(*) from " + s
                + "orders where status = 'PAID' and deleted = false and paid_at >= now() - interval '30 days'", Long.class)).append('\n');
        report.append("  orders per currency: ").append(jdbc.queryForList("select currency || '=' || count(*) from " + s
                + "orders group by currency order by currency", String.class)).append('\n');
        report.append("\nIndexes on orders / questions:\n");
        jdbc.queryForList("select indexdef from pg_indexes where schemaname = ? and tablename in ('orders','questions') "
                + "order by tablename, indexname", String.class, SCHEMA).forEach(def -> report.append("  ").append(def).append('\n'));

        List<String> failures = new ArrayList<>();
        StringBuilder verdicts = new StringBuilder("\n=== VERDICTS (with V97 present) ===\n");
        for (Q query : queries) {
            String natural = explain(query.sql(), false);
            String forced = explain(query.sql(), true);
            report.append("\n--- ").append(query.name()).append(" [planner default]\n").append(natural)
                    .append("\n--- ").append(query.name()).append(" [enable_seqscan=off]\n").append(forced).append('\n');
            verdicts.append(query.name()).append(": default planner -> ").append(summarisePlan(natural))
                    .append(" | forced -> ").append(summarisePlan(forced)).append('\n');
            if (query.mustUseIndexWhenForced() != null && !forced.contains(query.mustUseIndexWhenForced())) {
                failures.add(query.name() + " cannot use " + query.mustUseIndexWhenForced() + " even with seq scans disabled");
            }
        }

        // What the production code actually sends: bound parameters (status is a parameter, not a literal).
        // A partial index on status = 'PAID' can only be proven for a custom plan; check what happens after the
        // plan cache has seen the statement more than 5 times (auto) and with a forced generic plan.
        report.append("\n--- revenue current window, PREPARED with bound parameters (6 executions, then explain) [plan_cache_mode=auto]\n")
                .append(explainPrepared(revenueBody, "varchar, timestamptz, timestamptz", revenueArgs, false));
        report.append("\n--- revenue current window, PREPARED [plan_cache_mode=force_generic_plan] (worst case)\n")
                .append(explainPrepared(revenueBody, "varchar, timestamptz, timestamptz", revenueArgs, true));
        report.append("\n--- revenue previous window, PREPARED [plan_cache_mode=auto]\n")
                .append(explainPrepared(revenueBody, "varchar, timestamptz, timestamptz", previousArgs, false));
        String preparedAuto = explainPrepared(revenueBody, "varchar, timestamptz, timestamptz", revenueArgs, false);
        String preparedGeneric = explainPrepared(revenueBody, "varchar, timestamptz, timestamptz", revenueArgs, true);
        verdicts.append("revenue current window, prepared (auto, after 6 executions): ").append(summarisePlan(preparedAuto)).append('\n');
        verdicts.append("revenue current window, prepared (force_generic_plan): ").append(summarisePlan(preparedGeneric)).append('\n');

        // Cost of the whole 14-query revenue load of one cache miss, with and without V97.
        List<java.sql.Timestamp> boundaries = jdbc.queryForList("select (date_trunc('month', timezone('Asia/Ho_Chi_Minh', now())) "
                + "- k * interval '1 month') at time zone 'Asia/Ho_Chi_Minh' from generate_series(11, -1, -1) k",
                java.sql.Timestamp.class);
        assertThat(boundaries).hasSize(13);
        double withIndex = revenueLoadMillis(boundaries, 20);
        jdbc.execute("drop index " + s + V97_INDEX);
        double withoutIndex;
        String windowWithout;
        String oneMonthWithout;
        try {
            withoutIndex = revenueLoadMillis(boundaries, 20);
            windowWithout = explain(queries.get(0).sql(), false);
            oneMonthWithout = explain(queries.get(3).sql(), false);
        } finally {
            runMigrationScript(V97_RESOURCE);
            jdbc.execute("vacuum (analyze) " + s + "orders");
        }
        report.append("\n--- revenue current window WITHOUT idx_orders_paid_at [planner default]\n").append(windowWithout);
        report.append("\n--- revenue current partial month WITHOUT idx_orders_paid_at [planner default]\n").append(oneMonthWithout);
        verdicts.append(String.format(java.util.Locale.ROOT,
                "revenue load of one cache miss (2 windows + 12 months = 14 grouped queries, bound parameters, %d orders): "
                        + "%.1f ms WITH idx_orders_paid_at, %.1f ms WITHOUT%n", P4_ORDERS, withIndex, withoutIndex));
        verdicts.append("without V97 the current window plan is: ").append(summarisePlan(windowWithout)).append('\n');
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where schemaname = ? and indexname = ?",
                Integer.class, SCHEMA, V97_INDEX)).as("V97 index restored for the other tests").isEqualTo(1);

        report.append(verdicts);
        Files.writeString(Path.of("target", "overview-explain-plans-phase4.txt"), report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);

        assertThat(failures).isEmpty();
    }

    /** Plain-SQL seed: 100k orders over two years (several currencies, mixed statuses, 1 percent deleted) and 40k questions. */
    private void seedPhaseFour() {
        String paidAtOrCreatedAt = "now() - interval '730 days' + (g::float8 / " + P4_ORDERS + ") * interval '730 days'";
        long orderTemplate = insertBaseRow("orders", Map.of("tenant_id", "gen_random_uuid()", "plan_id", "gen_random_uuid()",
                "order_code", "7000000000000000", "amount", "100000", "currency", "'VND'", "status", "'PAID'",
                "paid_at", "now()"));
        cloneInterleaved("orders", new long[]{orderTemplate}, new UUID[]{UUID.randomUUID()}, P4_ORDERS, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("order_code", "(7000000000000000 + g)");
            o.put("tenant_id", "md5((g % 300)::text)::uuid");
            o.put("plan_id", "gen_random_uuid()");
            o.put("amount", "(10000 + (g % 1000) * 100)");
            o.put("currency", "(array['VND','VND','VND','VND','USD','USD','EUR','JPY'])[1 + g % 8]");
            o.put("status", "case when g % 100 < 70 then 'PAID' when g % 100 < 85 then 'CANCELLED' "
                    + "when g % 100 < 95 then 'EXPIRED' else 'PENDING' end");
            o.put("paid_at", "case when g % 100 < 70 then " + paidAtOrCreatedAt + " end");
            o.put("created_at", paidAtOrCreatedAt + " - interval '20 minutes'");
            o.put("deleted", "(g % 100 = 7)");
            return o;
        });
        long questionTemplate = insertBaseRow("questions", Map.of("task_type_key", "'READ_ALOUD'",
                "task_type_section", "'SPEAKING'", "visibility", "'SHARED'", "pool", "'EXAM'", "status", "'DRAFT'",
                "title", "'tpl'", "revision_number", "1", "is_current", "true", "version", "0"));
        cloneInterleaved("questions", new long[]{questionTemplate}, new UUID[]{UUID.randomUUID()}, P4_QUESTIONS, tenant -> {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("public_id", "gen_random_uuid()");
            o.put("revision_group_public_id", "gen_random_uuid()");
            o.put("title", "'q ' || g");
            o.put("task_type_section", "(array['LISTENING','SPEAKING','READING','WRITING'])[1 + g % 4]");
            o.put("status", "(array['APPROVED','APPROVED','APPROVED','APPROVED','APPROVED','DRAFT','DRAFT',"
                    + "'PENDING_APPROVAL','ARCHIVED','ARCHIVED'])[1 + g % 10]");
            o.put("pool", "case when g % 5 = 0 then 'PRACTICE' else 'EXAM' end");
            o.put("created_at", "now() - interval '730 days' + (g::float8 / " + P4_QUESTIONS + ") * interval '730 days'");
            o.put("deleted", "(g % 50 = 0)");
            return o;
        });
        for (String table : List.of("orders", "questions")) {
            jdbc.execute("vacuum (analyze) " + SCHEMA + "." + table);
        }
    }

    private static String summarisePlan(String plan) {
        List<String> nodes = new ArrayList<>();
        for (String line : plan.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("->")) {
                trimmed = trimmed.substring(2).trim();
            }
            if (trimmed.startsWith("Index") || trimmed.startsWith("Bitmap Index") || trimmed.startsWith("Seq Scan")
                    || trimmed.startsWith("Parallel Seq Scan") || trimmed.startsWith("Bitmap Heap")) {
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
                        "explain (analyze, timing off, summary on) " + sql)) {
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
        // Phase 3 realism fix: the seed used to hold only two tenants (tenant A = 50 percent of every table). Since V96 added
        // the platform-wide time indexes, a 50 percent tenant filter makes the planner prefer those narrow time-only indexes
        // over the tenant-leading ones, which is correct for that skew but not what production looks like. Twenty tenants
        // interleaved (tenant A = 5 percent) keep the same row counts and restore a production-like tenant selectivity.
        int tenantCount = 20;
        UUID[] tenants = new UUID[tenantCount];
        tenants[0] = tenantA;
        tenants[1] = tenantB;
        for (int i = 2; i < tenantCount; i++) {
            tenants[i] = UUID.randomUUID();
        }
        long[] attemptIds = new long[tenantCount];
        long[] userIds = new long[tenantCount];
        for (int i = 0; i < tenantCount; i++) {
            attemptIds[i] = attemptTemplate(tenants[i]);
            userIds[i] = userTemplate(tenants[i]);
        }
        // Sessions retain the two-tenant shape because the grouped-by-status query may legitimately use the existing
        // tenant-window index when a tenant owns a large share of the table; the created-window query still verifies the
        // dedicated V94 created-at index below.
        UUID[] sessionTenants = {tenantA, tenantB};
        long[] sessionIds = {sessionTemplate(tenantA), sessionTemplate(tenantB)};
        int attemptCopies = 2000; // x 20 tenants = 40000 rows, same totals as before
        int sessionCopies = 60000;

        cloneInterleaved("exam_attempts", attemptIds, tenants, attemptCopies, tenant -> {
            Map<String, String> overrides = new LinkedHashMap<>();
            overrides.put("public_id", "gen_random_uuid()");
            overrides.put("session_public_id", "gen_random_uuid()");
            overrides.put("student_public_id", "md5((g % 500)::text)::uuid");
            overrides.put("tenant_id", "'" + tenant + "'::uuid");
            overrides.put("status", "case when g % 30 = 0 then 'IN_PROGRESS' else 'SUBMITTED' end");
            overrides.put("started_at", "now() - (" + attemptCopies + " - g) * interval '5 hours' - interval '15 minutes'");
            overrides.put("submitted_at",
                    "case when g % 30 = 0 then null else now() - (" + attemptCopies + " - g) * interval '5 hours' end");
            return overrides;
        });
        cloneInterleaved("users", userIds, tenants, attemptCopies, tenant -> {
            Map<String, String> overrides = new LinkedHashMap<>();
            overrides.put("public_id", "gen_random_uuid()");
            overrides.put("tenant_id", "'" + tenant + "'::uuid");
            overrides.put("username", "'seed-' || g || '-' || t.tenant_id");
            overrides.put("email", "'seed-' || g || '-' || t.tenant_id || '@example.test'");
            overrides.put("created_at", "now() - (" + attemptCopies + " - g) * interval '5 hours'");
            return overrides;
        });
        jdbc.update("insert into " + SCHEMA + ".user_roles (user_id, role) select u.id, 'STUDENT' from " + SCHEMA
                + ".users u where u.username like 'seed-%'");
        cloneInterleaved("exam_sessions", sessionIds, sessionTenants, sessionCopies, tenant -> {
            Map<String, String> overrides = new LinkedHashMap<>();
            overrides.put("public_id", "gen_random_uuid()");
            overrides.put("session_code", "substr(md5(g::text || t.tenant_id::text), 1, 20)");
            // no_overlap_per_subscription forbids overlapping windows per subscription: one lane per row.
            overrides.put("subscription_id", "gen_random_uuid()");
            overrides.put("tenant_id", "'" + tenant + "'::uuid");
            overrides.put("status", "(array['DRAFT','SCHEDULED','OPEN','CLOSED'])[1 + g % 4]");
            overrides.put("created_at", "now() - (" + sessionCopies + " - g) * interval '10 minutes'");
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
