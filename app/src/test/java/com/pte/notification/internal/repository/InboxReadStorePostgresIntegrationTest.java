package com.pte.notification.internal.repository;

import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.InboxRecipient;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxReadFilter;
import com.pte.notification.domain.enums.InboxTargetType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real PostgreSQL coverage for snapshot ownership, anchoring and stream-lock read-all. */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class InboxReadStorePostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");
    private final UUID host = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();
    private DriverManagerDataSource adminDataSource;
    private DriverManagerDataSource dataSource;
    private String schema;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private InboxDeliveryStore deliveryStore;
    private InboxReadStore readStore;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("INBOX_TEST_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "INBOX_TEST_DB_URL absent: real PostgreSQL test explicitly skipped");
        String user = System.getenv().getOrDefault("INBOX_TEST_DB_USER", "codex_inbox_test");
        String password = System.getenv().getOrDefault("INBOX_TEST_DB_PASSWORD", "");
        adminDataSource = new DriverManagerDataSource(url, user, password);
        schema = "inbox_read_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?")
                + "currentSchema=" + schema, user, password);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE users (public_id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE tenants (public_id UUID PRIMARY KEY)");
        jdbc.update("INSERT INTO users(public_id) VALUES (?)", host);
        jdbc.update("INSERT INTO tenants(public_id) VALUES (?)", tenant);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V72__notification_inbox_foundation.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V73__notification_inbox_snapshots.sql"));
        }
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        deliveryStore = new InboxDeliveryStore(new NamedParameterJdbcTemplate(dataSource));
        readStore = new InboxReadStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterEach
    void cleanOwnSchema() {
        if (schema != null && adminDataSource != null) {
            if (!schema.matches("inbox_read_test_[a-f0-9]{32}")) {
                throw new AssertionError("Refusing cleanup outside the exact generated test schema");
            }
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migrationCreatesRecipientBoundSnapshotAndAnchoredPage() {
        deliver("first", NOW);
        InboxReadStore.Snapshot snapshot = tx(() -> readStore.createSnapshot(host, tenant, NOW,
                NOW.plusSeconds(900)));
        deliver("second", NOW);

        InboxReadStore.Page page = tx(() -> readStore.page(host, tenant, InboxReadFilter.ALL, null,
                snapshot.upperSequence(), 0, 20));

        assertThat(snapshot.upperSequence()).isEqualTo(1);
        assertThat(page.rows()).hasSize(1);
        assertThat(page.rows().getFirst().title()).isEqualTo("Title");
        assertThat(tx(() -> readStore.findSnapshot(snapshot.token(), host, tenant, NOW.plusSeconds(1))))
                .isPresent();
    }

    @Test
    void readAllWaitsForDeliveryCommitAndLeavesLaterArrivalUnread() throws Exception {
        append("first", NOW);
        InboxDeliveryClaim claim = claim(NOW);
        CountDownLatch deliveryLocked = new CountDownLatch(1);
        CountDownLatch releaseDelivery = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Void> delivery = executor.submit(() -> tx(() -> {
                InboxDeliveryRecord record = deliveryStore.findClaim(claim, NOW).orElseThrow();
                deliveryStore.deliver(record, claim, NOW);
                deliveryLocked.countDown();
                await(releaseDelivery);
                return null;
            }));
            assertThat(deliveryLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<InboxReadStore.MarkAllResult> readAll = executor.submit(() ->
                    tx(() -> readStore.markAll(host, tenant, null, NOW)));
            try {
                assertThatThrown(readAll, 200);
            } finally {
                releaseDelivery.countDown();
            }
            delivery.get(5, TimeUnit.SECONDS);
            assertThat(readAll.get(5, TimeUnit.SECONDS).markedCount()).isEqualTo(1);
        } finally {
            releaseDelivery.countDown();
        }

        deliver("second", NOW);
        assertThat(tx(() -> readStore.unreadCount(host, tenant).count())).isEqualTo(1);
    }

    private InboxDeliveryClaim claim(Instant now) {
        return tx(() -> deliveryStore.claimBatch(now, now.plusSeconds(30), 10, 5).getFirst());
    }

    private void deliver(String key, Instant now) {
        tx(() -> {
            deliveryStore.append(new InboxNotificationRequested(1, key,
                    InboxNotificationType.PLATFORM_ANNOUNCEMENT, InboxCategory.SYSTEM_NOTICE,
                    InboxImportance.INFO, "Title", "Body", InboxTargetType.ANNOUNCEMENT, UUID.randomUUID(),
                    List.of(new InboxRecipient(host, tenant))), now);
            InboxDeliveryClaim claim = deliveryStore.claimBatch(now, now.plusSeconds(30), 10, 5).getFirst();
            InboxDeliveryRecord record = deliveryStore.findClaim(claim, now).orElseThrow();
            deliveryStore.deliver(record, claim, now);
            return null;
        });
    }

    private void append(String key, Instant now) {
        tx(() -> {
            deliveryStore.append(new InboxNotificationRequested(1, key,
                    InboxNotificationType.PLATFORM_ANNOUNCEMENT, InboxCategory.SYSTEM_NOTICE,
                    InboxImportance.INFO, "Title", "Body", InboxTargetType.ANNOUNCEMENT, UUID.randomUUID(),
                    List.of(new InboxRecipient(host, tenant))), now);
            return null;
        });
    }

    private <T> T tx(java.util.function.Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private void assertThatThrown(Future<?> future, long timeoutMillis) {
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            throw new AssertionError("read-all did not wait for the delivery stream lock");
        } catch (TimeoutException expected) {
            // Expected: the delivery transaction still owns the recipient stream row.
        } catch (Exception unexpected) {
            throw new AssertionError("read-all failed before the delivery lock was released", unexpected);
        }
    }
}
