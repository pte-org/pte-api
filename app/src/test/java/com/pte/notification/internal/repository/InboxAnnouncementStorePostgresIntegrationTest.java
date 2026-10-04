package com.pte.notification.internal.repository;

import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.InboxRecipient;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
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

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class InboxAnnouncementStorePostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-03T13:00:00Z");
    private final UUID admin = UUID.randomUUID();
    private final UUID host = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();
    private final UUID announcement = UUID.randomUUID();
    private DriverManagerDataSource adminDataSource;
    private DriverManagerDataSource dataSource;
    private String schema;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private InboxAnnouncementStore announcementStore;
    private InboxDeliveryStore deliveryStore;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("INBOX_TEST_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "INBOX_TEST_DB_URL absent: real PostgreSQL test explicitly skipped");
        String user = System.getenv().getOrDefault("INBOX_TEST_DB_USER", "codex_inbox_test");
        String password = System.getenv().getOrDefault("INBOX_TEST_DB_PASSWORD", "");
        adminDataSource = new DriverManagerDataSource(url, user, password);
        schema = "inbox_announcement_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?")
                + "currentSchema=" + schema, user, password);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE users (public_id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE tenants (public_id UUID PRIMARY KEY)");
        jdbc.update("INSERT INTO users(public_id) VALUES (?), (?), (?)", admin, host, UUID.randomUUID());
        jdbc.update("INSERT INTO tenants(public_id) VALUES (?)", tenant);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V72__notification_inbox_foundation.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V73__notification_inbox_snapshots.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V74__notification_announcement_query_indexes.sql"));
        }
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        announcementStore = new InboxAnnouncementStore(new NamedParameterJdbcTemplate(dataSource));
        deliveryStore = new InboxDeliveryStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterEach
    void cleanOwnSchema() {
        if (schema != null && adminDataSource != null) {
            if (!schema.matches("inbox_announcement_test_[a-f0-9]{32}")) {
                throw new AssertionError("Refusing cleanup outside the exact generated test schema");
            }
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void draftVersionPublishAndDeliveryReadSummaryArePersistedAtomically() {
        var draft = tx(() -> announcementStore.insert(announcement, admin, "Maintenance", "Body",
                InboxCategory.MAINTENANCE, InboxImportance.IMPORTANT, NOW, NOW.plusSeconds(3600), null, NOW));
        assertThat(draft.version()).isZero();
        assertThat(tx(() -> announcementStore.updateDraft(announcement, 0, "Maintenance 2", "Body 2",
                InboxCategory.SYSTEM_NOTICE, InboxImportance.INFO, null, null, NOW))).isTrue();

        var edited = tx(() -> announcementStore.lockForUpdate(announcement).orElseThrow());
        UUID content = tx(() -> deliveryStore.append(new InboxNotificationRequested(1,
                "announcement:" + announcement + ":published:v" + edited.version(),
                InboxNotificationType.PLATFORM_ANNOUNCEMENT, edited.category(), edited.importance(), edited.title(),
                edited.body(), InboxTargetType.ANNOUNCEMENT, announcement,
                List.of(new InboxRecipient(host, tenant))), NOW));
        assertThat(tx(() -> announcementStore.markPublished(announcement, edited.version(), content, NOW)))
                .isPresent();

        assertThat(tx(() -> announcementStore.updateDraft(announcement, edited.version(), "Illegal", "Mutation",
                InboxCategory.SYSTEM_NOTICE, InboxImportance.INFO, null, null, NOW))).isFalse();
        assertThat(tx(() -> announcementStore.find(announcement).orElseThrow().delivery().audienceCount()))
                .isEqualTo(1);
        assertThat(tx(() -> announcementStore.find(announcement).orElseThrow().delivery().pendingCount()))
                .isEqualTo(1);

        InboxDeliveryClaim claim = tx(() -> deliveryStore.claimBatch(NOW, NOW.plusSeconds(30), 10, 5).getFirst());
        tx(() -> {
            InboxDeliveryRecord record = deliveryStore.findClaim(claim, NOW).orElseThrow();
            deliveryStore.deliver(record, claim, NOW);
            return null;
        });
        assertThat(tx(() -> announcementStore.find(announcement).orElseThrow().delivery().deliveredCount()))
                .isEqualTo(1);

        tx(() -> {
            jdbc.update("UPDATE notification_inbox_items SET read_at=? WHERE content_public_id=?",
                    java.sql.Timestamp.from(NOW), content);
            return null;
        });
        assertThat(tx(() -> announcementStore.find(announcement).orElseThrow().delivery().readCount()))
                .isEqualTo(1);
    }

    private <T> T tx(java.util.function.Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }
}
