package com.pte.notification.internal.repository;

import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.InboxRecipient;
import com.pte.notification.domain.InboxAnnouncement;
import com.pte.notification.domain.InboxContent;
import com.pte.notification.domain.InboxDeliveryIntent;
import com.pte.notification.domain.InboxItem;
import com.pte.notification.domain.InboxRecipientStream;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.service.InboxAppendService;
import com.pte.notification.internal.listener.InboxRequestListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real PostgreSQL only. Every test owns a random schema; public is never touched. */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class InboxDeliveryStorePostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");
    private DriverManagerDataSource adminDataSource;
    private DriverManagerDataSource dataSource;
    private String schema;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private InboxDeliveryStore store;
    private final UUID admin = UUID.randomUUID();
    private final UUID secondAdmin = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("INBOX_TEST_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "INBOX_TEST_DB_URL absent: real PostgreSQL test explicitly skipped");
        assertThat(url).startsWith("jdbc:postgresql:");
        String user = System.getenv().getOrDefault("INBOX_TEST_DB_USER", "codex_inbox_test");
        String password = System.getenv().getOrDefault("INBOX_TEST_DB_PASSWORD", "");
        adminDataSource = new DriverManagerDataSource(url, user, password);
        schema = "inbox_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?")
                + "currentSchema=" + schema, user, password);
        jdbc = new JdbcTemplate(dataSource);
        // Only the FK identities V72 needs. Never load historical application data/migrations.
        jdbc.execute("CREATE TABLE users (public_id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE tenants (public_id UUID PRIMARY KEY)");
        jdbc.update("INSERT INTO users(public_id) VALUES (?), (?)", admin, secondAdmin);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V72__notification_inbox_foundation.sql"));
        }
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        store = new InboxDeliveryStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterEach
    void cleanOwnSchema() {
        if (schema != null && adminDataSource != null) {
            if (!schema.matches("inbox_test_[a-f0-9]{32}")) {
                throw new AssertionError("Refusing cleanup outside the exact generated test schema");
            }
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void fiveInboxEntityMappingsValidateAgainstActualV71WithoutSchemaWrites() {
        org.hibernate.cfg.Configuration configuration = new org.hibernate.cfg.Configuration();
        configuration.addAnnotatedClass(InboxContent.class);
        configuration.addAnnotatedClass(InboxDeliveryIntent.class);
        configuration.addAnnotatedClass(InboxItem.class);
        configuration.addAnnotatedClass(InboxRecipientStream.class);
        configuration.addAnnotatedClass(InboxAnnouncement.class);
        configuration.setPhysicalNamingStrategy(new org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl());
        configuration.getProperties().put("hibernate.connection.datasource", dataSource);
        configuration.setProperty("hibernate.hbm2ddl.auto", "validate");
        configuration.setProperty("hibernate.default_schema", schema);
        try (org.hibernate.SessionFactory factory = configuration.buildSessionFactory()) {
            assertThat(factory.getMetamodel().getEntities()).hasSize(5);
            assertThat(factory.getMetamodel().getEntities().stream().map(entity -> entity.getJavaType().getSimpleName()))
                    .containsExactlyInAnyOrder("InboxContent", "InboxDeliveryIntent", "InboxItem",
                            "InboxRecipientStream", "InboxAnnouncement");
        }
    }

    @Test
    void appendRollbackLeavesNeitherContentNorDelivery() {
        assertThatThrownBy(() -> tx(() -> {
            store.append(request("rollback", "Body", List.of(new InboxRecipient(admin, null))), NOW);
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("notification_inbox_contents")).isZero();
        assertThat(count("notification_inbox_deliveries")).isZero();
    }

    @Test
    void beforeCommitListenerPersistsOnlyInsideCommittedOriginTransaction() {
        try (AnnotationConfigApplicationContext context = context()) {
            InboxAppendService appender = context.getBean(InboxAppendService.class);
            InboxNotificationRequested event = request("listener", "Body", List.of(new InboxRecipient(admin, null)));
            assertThatThrownBy(() -> appender.append(event)).isInstanceOf(IllegalTransactionStateException.class);
            context.publishEvent(event); // fallbackExecution=false: no originating transaction, no intent.
            assertThat(count("notification_inbox_deliveries")).isZero();
            assertThatThrownBy(() -> tx(() -> {
                context.publishEvent(event);
                throw new IllegalStateException("source transaction rollback");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(count("notification_inbox_deliveries")).isZero();
            tx(() -> { context.publishEvent(event); return null; });
            assertThat(count("notification_inbox_contents")).isEqualTo(1);
            assertThat(count("notification_inbox_deliveries")).isEqualTo(1);
        }
    }

    @Test
    void tenConcurrentAppendsDeduplicateBothNullTenantAdminRecipients() throws Exception {
        InboxNotificationRequested event = request("concurrent", "Body",
                List.of(new InboxRecipient(admin, null), new InboxRecipient(secondAdmin, null)));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(10)) {
            List<Future<Void>> work = IntStream.range(0, 10).mapToObj(index -> executor.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                tx(() -> { store.append(event, NOW); return null; });
                return (Void) null;
            })).toList();
            start.countDown();
            for (Future<Void> job : work) { job.get(15, TimeUnit.SECONDS); }
        }
        assertThat(count("notification_inbox_contents")).isEqualTo(1);
        assertThat(count("notification_inbox_deliveries")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_inbox_deliveries WHERE tenant_id IS NULL",
                Long.class)).isEqualTo(2L);
    }

    @Test
    void repeatedIdentityWithChangedPayloadCannotRewriteHistory() {
        append("immutable");
        assertThatThrownBy(() -> tx(() -> {
            store.append(request("immutable", "Changed body", List.of(new InboxRecipient(admin, null))), NOW);
            return null;
        })).isInstanceOf(com.pte.notification.internal.exception.InboxNotificationException.class);
        assertThat(jdbc.queryForObject("SELECT body FROM notification_inbox_contents", String.class)).isEqualTo("Body");
        assertThat(count("notification_inbox_deliveries")).isEqualTo(1);
    }

    @Test
    void overlappingClaimsSkipLockedRowsWithoutDuplicateOwnership() throws Exception {
        for (int index = 0; index < 4; index++) { append("batch-" + index); }
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<List<InboxDeliveryClaim>> first = executor.submit(() -> tx(() -> {
                List<InboxDeliveryClaim> claims = store.claimBatch(NOW, NOW.plusSeconds(30), 2, 5);
                firstClaimed.countDown();
                await(release);
                return claims;
            }));
            assertThat(firstClaimed.await(5, TimeUnit.SECONDS)).isTrue();
            List<InboxDeliveryClaim> second;
            try {
                second = executor.submit(() -> tx(() -> store.claimBatch(NOW, NOW.plusSeconds(30), 2, 5)))
                        .get(5, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            List<InboxDeliveryClaim> firstClaims = first.get(5, TimeUnit.SECONDS);
            assertThat(firstClaims).hasSize(2);
            assertThat(second).hasSize(2);
            HashSet<UUID> all = new HashSet<>();
            firstClaims.forEach(claim -> assertThat(all.add(claim.publicId())).isTrue());
            second.forEach(claim -> assertThat(all.add(claim.publicId())).isTrue());
            assertThat(all).hasSize(4);
        } finally { release.countDown(); }
    }

    @Test
    void expiredCrashLeaseCanBeReclaimedButOldTokenCannotDeliver() {
        append("lease");
        InboxDeliveryClaim old = claim(NOW, 5);
        Instant later = NOW.plusSeconds(31);
        InboxDeliveryClaim current = claim(later, 5);
        assertThat(current.publicId()).isEqualTo(old.publicId());
        assertThat(current.token()).isNotEqualTo(old.token());
        assertThat(current.attempts()).isEqualTo(2);
        assertThat(tx(() -> store.findClaim(old, later))).isEmpty();
        assertThat(tx(() -> store.lockClaim(old, later))).isFalse();
        deliver(current, later);
        assertThat(count("notification_inbox_items")).isEqualTo(1);
        assertThat(status(current)).isEqualTo("DELIVERED");
        assertThat(tx(() -> store.lockClaim(old, later))).isFalse();
    }

    @Test
    void rollbackAfterDeliveryRestoresProcessingStateAndStreamSequence() {
        append("atomic");
        InboxDeliveryClaim claim = claim(NOW, 5);
        assertThatThrownBy(() -> tx(() -> {
            deliverInsideTransaction(claim, NOW);
            throw new IllegalStateException("crash before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("notification_inbox_items")).isZero();
        assertThat(status(claim)).isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(last_sequence), 0) FROM notification_inbox_streams",
                Long.class)).isZero();
        deliver(claim, NOW);
        assertThat(count("notification_inbox_items")).isEqualTo(1);
        assertThat(status(claim)).isEqualTo("DELIVERED");
    }

    @Test
    void readAllWatermarkWaitsForDeliveryCommitAndLeavesLaterArrivalUnread() throws Exception {
        append("first");
        InboxDeliveryClaim first = claim(NOW, 5);
        CountDownLatch deliveredUncommitted = new CountDownLatch(1);
        CountDownLatch releaseDelivery = new CountDownLatch(1);
        CountDownLatch readerAtLock = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Void> delivery = executor.submit(() -> tx(() -> {
                deliverInsideTransaction(first, NOW);
                deliveredUncommitted.countDown();
                await(releaseDelivery);
                return null;
            }));
            assertThat(deliveredUncommitted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Long> readAll = executor.submit(() -> tx(() -> {
                readerAtLock.countDown();
                // Mirror the later read-all contract: acquire/create the SAME recipient stream lock.
                jdbc.update("INSERT INTO notification_inbox_streams(public_id,recipient_user_public_id,tenant_id) "
                        + "VALUES (?, ?, NULL) ON CONFLICT (recipient_user_public_id) DO NOTHING", UUID.randomUUID(), admin);
                Long watermark = jdbc.queryForObject("SELECT last_sequence FROM notification_inbox_streams "
                        + "WHERE recipient_user_public_id = ? FOR UPDATE", Long.class, admin);
                jdbc.update("UPDATE notification_inbox_items SET read_at = ? WHERE recipient_user_public_id = ? "
                        + "AND sequence_no <= ? AND read_at IS NULL", java.sql.Timestamp.from(NOW), admin, watermark);
                return watermark;
            }));
            assertThat(readerAtLock.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> readAll.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { releaseDelivery.countDown(); }
            delivery.get(5, TimeUnit.SECONDS);
            assertThat(readAll.get(5, TimeUnit.SECONDS)).isEqualTo(1L);
        } finally { releaseDelivery.countDown(); }
        append("second");
        deliver(claim(NOW, 5), NOW);
        assertThat(jdbc.queryForList("SELECT sequence_no FROM notification_inbox_items WHERE read_at IS NULL",
                Long.class)).containsExactly(2L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_inbox_items WHERE read_at IS NOT NULL",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void exhaustedCrashLeaseFailsAndManualRetryKeepsOriginalIdentity() {
        append("exhausted");
        InboxDeliveryClaim first = claim(NOW, 1);
        Instant later = NOW.plusSeconds(31);
        assertThat(tx(() -> store.exhaustExpired(later, 1))).isEqualTo(1);
        assertThat(status(first)).isEqualTo("FAILED");
        assertThat(tx(() -> store.claimBatch(later, later.plusSeconds(30), 50, 1))).isEmpty();
        assertThat(tx(() -> store.retryFailed(first.publicId(), later))).isTrue();
        InboxDeliveryClaim retried = claim(later, 5);
        assertThat(retried.publicId()).isEqualTo(first.publicId());
        assertThat(retried.token()).isNotEqualTo(first.token());
        assertThat(count("notification_inbox_contents")).isEqualTo(1);
        assertThat(count("notification_inbox_deliveries")).isEqualTo(1);
        assertThat(tx(() -> store.retryFailed(first.publicId(), later))).isFalse();
    }

    @Test
    void exhaustExpiredOnlyRecoversConfiguredBatchAndLeavesRemainder() {
        Instant later = NOW.plusSeconds(31);
        tx(() -> {
            for (int index = 0; index <= InboxConstants.RECOVERY_BATCH_LIMIT; index++) {
                store.append(request("exhausted-batch-" + index, "Body",
                        List.of(new InboxRecipient(admin, null))), NOW);
            }
            return null;
        });

        List<InboxDeliveryClaim> claims = tx(() -> store.claimBatch(NOW, NOW.plusSeconds(30),
                InboxConstants.RECOVERY_BATCH_LIMIT + 1, 1));
        assertThat(claims).hasSize(InboxConstants.RECOVERY_BATCH_LIMIT + 1);

        assertThat(tx(() -> store.exhaustExpired(later, 1)))
                .isEqualTo(InboxConstants.RECOVERY_BATCH_LIMIT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_inbox_deliveries WHERE status='FAILED'",
                Long.class)).isEqualTo((long) InboxConstants.RECOVERY_BATCH_LIMIT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_inbox_deliveries WHERE status='PROCESSING'",
                Long.class)).isEqualTo(1L);
    }

    private AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.register(ListenerConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration
    @EnableTransactionManagement
    static class ListenerConfiguration {
        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean InboxDeliveryStore deliveryStore(DataSource dataSource) {
            return new InboxDeliveryStore(new NamedParameterJdbcTemplate(dataSource));
        }
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean InboxAppendService appender(InboxDeliveryStore store, Clock clock) {
            return new InboxAppendService(store, clock);
        }
        @Bean InboxRequestListener listener(InboxAppendService appender) { return new InboxRequestListener(appender); }
        @Bean static TransactionalEventListenerFactory transactionalEventListenerFactory() {
            return new TransactionalEventListenerFactory();
        }
    }

    private void append(String key) {
        tx(() -> { store.append(request(key, "Body", List.of(new InboxRecipient(admin, null))), NOW); return null; });
    }

    private InboxNotificationRequested request(String key, String body, List<InboxRecipient> audience) {
        return new InboxNotificationRequested(1, key, InboxNotificationType.APPLICATION_SUBMITTED,
                InboxCategory.APPLICATION, InboxImportance.INFO, "Title", body, InboxTargetType.APPLICATION,
                target, audience);
    }

    private InboxDeliveryClaim claim(Instant now, int maxAttempts) {
        return tx(() -> store.claimBatch(now, now.plusSeconds(30), 1, maxAttempts)).getFirst();
    }

    private void deliver(InboxDeliveryClaim claim, Instant now) {
        tx(() -> { deliverInsideTransaction(claim, now); return null; });
    }

    private void deliverInsideTransaction(InboxDeliveryClaim claim, Instant now) {
        InboxDeliveryRecord record = store.findClaim(claim, now).orElseThrow();
        assertThat(store.lockClaim(claim, now)).isTrue();
        store.deliver(record, claim, now);
    }

    private String status(InboxDeliveryClaim claim) {
        return jdbc.queryForObject("SELECT status FROM notification_inbox_deliveries WHERE public_id = ?",
                String.class, claim.publicId());
    }

    private long count(String table) {
        if (!table.matches("notification_inbox_[a-z_]+")) { throw new AssertionError("Invalid test table"); }
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private <T> T tx(Supplier<T> action) { return transaction.execute(status -> action.get()); }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
