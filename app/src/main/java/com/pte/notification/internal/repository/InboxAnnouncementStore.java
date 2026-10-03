package com.pte.notification.internal.repository;

import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Notification-owned draft/publication SQL with row locks for the publication transition. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class InboxAnnouncementStore {
    private final NamedParameterJdbcTemplate jdbc;

    public InboxAnnouncementStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AnnouncementRow insert(UUID publicId, UUID authorUserPublicId, String title, String body,
            InboxCategory category, InboxImportance importance, Instant affectedFrom, Instant affectedUntil,
            UUID correctionOfPublicId, Instant now) {
        MapSqlParameterSource params = base(now)
                .addValue("publicId", publicId)
                .addValue("author", authorUserPublicId)
                .addValue("title", title)
                .addValue("body", body)
                .addValue("category", category.name())
                .addValue("importance", importance.name())
                .addValue("affectedFrom", utc(affectedFrom), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("affectedUntil", utc(affectedUntil), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("correctionOf", correctionOfPublicId, Types.OTHER);
        return jdbc.query("""
                INSERT INTO notification_inbox_announcements
                  (public_id,author_user_public_id,title,body,category,importance,affected_from,affected_until,
                   correction_of_public_id,version,created_at,updated_at)
                VALUES (:publicId,:author,:title,:body,:category,:importance,:affectedFrom,:affectedUntil,
                        :correctionOf,0,:now,:now)
                RETURNING public_id,author_user_public_id,title,body,category,importance,affected_from,affected_until,
                          published_content_public_id,published_at,correction_of_public_id,version,created_at,updated_at
                """, params, (rs, row) -> map(rs, DeliverySummary.empty())).getFirst();
    }

    public Optional<AnnouncementRow> find(UUID publicId) {
        return queryById(publicId, false);
    }

    public Optional<AnnouncementRow> lockForUpdate(UUID publicId) {
        return queryById(publicId, true);
    }

    public Page page(int offset, int limit) {
        long total = jdbc.queryForObject("""
                SELECT count(*) FROM notification_inbox_announcements
                WHERE deleted=FALSE
                """, new MapSqlParameterSource(), Long.class);
        List<AnnouncementRow> rows = jdbc.query(select() + """
                WHERE a.deleted=FALSE
                ORDER BY a.created_at DESC,a.id DESC LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset),
                (rs, row) -> map(rs, deliverySummary(rs)));
        return new Page(rows, total);
    }

    public boolean updateDraft(UUID publicId, long expectedVersion, String title, String body,
            InboxCategory category, InboxImportance importance, Instant affectedFrom, Instant affectedUntil,
            Instant now) {
        return jdbc.update("""
                UPDATE notification_inbox_announcements
                SET title=:title,body=:body,category=:category,importance=:importance,
                    affected_from=:affectedFrom,affected_until=:affectedUntil,
                    version=version+1,updated_at=:now
                WHERE public_id=:publicId AND deleted=FALSE AND published_content_public_id IS NULL
                  AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("publicId", publicId).addValue("expectedVersion", expectedVersion)
                .addValue("title", title).addValue("body", body).addValue("category", category.name())
                .addValue("importance", importance.name())
                .addValue("affectedFrom", utc(affectedFrom), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("affectedUntil", utc(affectedUntil), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("now", utc(now))) == 1;
    }

    public boolean deleteDraft(UUID publicId, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE notification_inbox_announcements
                SET deleted=TRUE,version=version+1,updated_at=:now
                WHERE public_id=:publicId AND deleted=FALSE AND published_content_public_id IS NULL
                  AND version=:expectedVersion
                """, new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("expectedVersion", expectedVersion).addValue("now", utc(now))) == 1;
    }

    public Optional<AnnouncementRow> markPublished(UUID publicId, long expectedVersion, UUID contentPublicId,
            Instant publishedAt) {
        int changed = jdbc.update("""
                UPDATE notification_inbox_announcements
                SET published_content_public_id=:contentId,published_at=:publishedAt,
                    version=version+1,updated_at=:publishedAt
                WHERE public_id=:publicId AND deleted=FALSE AND published_content_public_id IS NULL
                  AND version=:expectedVersion
                """, new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("expectedVersion", expectedVersion).addValue("contentId", contentPublicId)
                .addValue("publishedAt", utc(publishedAt)));
        return changed == 1 ? find(publicId) : Optional.empty();
    }

    private Optional<AnnouncementRow> queryById(UUID publicId, boolean lock) {
        String suffix = lock ? "\nFOR UPDATE" : "";
        return jdbc.query(select() + " WHERE a.public_id=:publicId AND a.deleted=FALSE" + suffix,
                new MapSqlParameterSource("publicId", publicId),
                (rs, row) -> map(rs, deliverySummary(rs))).stream().findFirst();
    }

    private String select() {
        String stats = """
                ,COALESCE((SELECT count(*) FROM notification_inbox_deliveries d
                    WHERE d.content_public_id=a.published_content_public_id AND d.deleted=FALSE),0) AS audience_count
                ,COALESCE((SELECT count(*) FROM notification_inbox_deliveries d
                    WHERE d.content_public_id=a.published_content_public_id
                      AND d.status IN ('PENDING','PROCESSING') AND d.deleted=FALSE),0) AS pending_count
                ,COALESCE((SELECT count(*) FROM notification_inbox_deliveries d
                    WHERE d.content_public_id=a.published_content_public_id AND d.status='DELIVERED' AND d.deleted=FALSE),0) AS delivered_count
                ,COALESCE((SELECT count(*) FROM notification_inbox_deliveries d
                    WHERE d.content_public_id=a.published_content_public_id AND d.status='FAILED' AND d.deleted=FALSE),0) AS failed_count
                ,COALESCE((SELECT count(*) FROM notification_inbox_deliveries d
                    WHERE d.content_public_id=a.published_content_public_id AND d.status='SUPPRESSED' AND d.deleted=FALSE),0) AS suppressed_count
                ,COALESCE((SELECT count(*) FROM notification_inbox_items i
                    WHERE i.content_public_id=a.published_content_public_id AND i.read_at IS NOT NULL AND i.deleted=FALSE),0) AS read_count
                """;
        return """
                SELECT a.public_id,a.author_user_public_id,a.title,a.body,a.category,a.importance,
                       a.affected_from,a.affected_until,a.published_content_public_id,a.published_at,
                       a.correction_of_public_id,a.version,a.created_at,a.updated_at
                """ + stats + """
                FROM notification_inbox_announcements a
                """;
    }

    private AnnouncementRow map(java.sql.ResultSet rs, DeliverySummary summary) throws java.sql.SQLException {
        return new AnnouncementRow(rs.getObject("public_id", UUID.class),
                rs.getObject("author_user_public_id", UUID.class), rs.getString("title"), rs.getString("body"),
                InboxCategory.valueOf(rs.getString("category")), InboxImportance.valueOf(rs.getString("importance")),
                instant(rs, "affected_from"), instant(rs, "affected_until"),
                rs.getObject("published_content_public_id", UUID.class), instant(rs, "published_at"),
                rs.getObject("correction_of_public_id", UUID.class), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"), summary);
    }

    private DeliverySummary deliverySummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DeliverySummary(rs.getLong("audience_count"), rs.getLong("pending_count"),
                rs.getLong("delivered_count"), rs.getLong("failed_count"), rs.getLong("suppressed_count"),
                rs.getLong("read_count"));
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static MapSqlParameterSource base(Instant now) {
        return new MapSqlParameterSource().addValue("now", utc(now));
    }

    private static OffsetDateTime utc(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    public record AnnouncementRow(UUID publicId, UUID authorUserPublicId, String title, String body,
            InboxCategory category, InboxImportance importance, Instant affectedFrom, Instant affectedUntil,
            UUID publishedContentPublicId, Instant publishedAt, UUID correctionOfPublicId, long version,
            Instant createdAt, Instant updatedAt, DeliverySummary delivery) {
        public boolean published() { return publishedContentPublicId != null; }
    }

    public record DeliverySummary(long audienceCount, long pendingCount, long deliveredCount, long failedCount,
            long suppressedCount, long readCount) {
        public static DeliverySummary empty() { return new DeliverySummary(0, 0, 0, 0, 0, 0); }
    }

    public record Page(List<AnnouncementRow> rows, long totalElements) { }
}
