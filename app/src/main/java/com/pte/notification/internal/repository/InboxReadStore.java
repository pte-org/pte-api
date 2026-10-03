package com.pte.notification.internal.repository;

import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxReadFilter;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.exception.InboxNotificationException;
import org.springframework.http.HttpStatus;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Recipient-owned inbox reads and watermark mutations; delivery remains in InboxDeliveryStore. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class InboxReadStore {
    private final NamedParameterJdbcTemplate jdbc;

    public InboxReadStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Snapshot createSnapshot(UUID recipient, UUID tenant, Instant now, Instant expiresAt) {
        Stream stream = lockOrCreateStream(recipient, tenant, true);
        jdbc.update("""
                WITH expired AS (
                  SELECT id FROM notification_inbox_snapshots
                  WHERE expires_at<=:now
                  ORDER BY expires_at,id LIMIT :limit
                )
                DELETE FROM notification_inbox_snapshots s USING expired
                WHERE s.id=expired.id
                """, new MapSqlParameterSource().addValue("now", utc(now))
                .addValue("limit", InboxConstants.SNAPSHOT_CLEANUP_BATCH_LIMIT));
        UUID token = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notification_inbox_snapshots
                  (public_id,recipient_user_public_id,tenant_id,upper_sequence,read_revision,created_at,expires_at)
                VALUES (:token,:recipient,:tenant,:upperSequence,:readRevision,:now,:expiresAt)
                """, snapshotParams(token, recipient, tenant, stream.lastSequence(), stream.readRevision(), now,
                expiresAt));
        return new Snapshot(token, recipient, tenant, stream.lastSequence(), stream.readRevision(), expiresAt);
    }

    public Optional<Snapshot> findSnapshot(UUID token, UUID recipient, UUID tenant, Instant now) {
        return jdbc.query("""
                SELECT public_id,recipient_user_public_id,tenant_id,upper_sequence,read_revision,expires_at
                FROM notification_inbox_snapshots
                WHERE public_id=:token AND recipient_user_public_id=:recipient
                  AND tenant_id IS NOT DISTINCT FROM CAST(:tenant AS UUID) AND expires_at>:now
                """, new MapSqlParameterSource().addValue("token", token)
                .addValue("recipient", recipient).addValue("tenant", tenant, Types.OTHER).addValue("now", utc(now)),
                (rs, rowNum) -> new Snapshot(rs.getObject("public_id", UUID.class),
                        rs.getObject("recipient_user_public_id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getLong("upper_sequence"), rs.getLong("read_revision"),
                        rs.getTimestamp("expires_at").toInstant())).stream().findFirst();
    }

    public Page page(UUID recipient, UUID tenant, InboxReadFilter filter, InboxCategory category,
            long upperSequence, int offset, int limit) {
        MapSqlParameterSource params = ownerParams(recipient, tenant)
                .addValue("upperSequence", upperSequence)
                .addValue("unreadOnly", filter == InboxReadFilter.UNREAD)
                .addValue("category", category == null ? null : category.name(), Types.VARCHAR)
                .addValue("offset", offset).addValue("limit", limit);
        long total = jdbc.queryForObject("""
                SELECT count(*)
                FROM notification_inbox_items i
                JOIN notification_inbox_contents c ON c.public_id=i.content_public_id
                WHERE i.deleted=FALSE AND c.deleted=FALSE
                  AND i.recipient_user_public_id=:recipient
                  AND i.tenant_id IS NOT DISTINCT FROM CAST(:tenant AS UUID)
                  AND i.sequence_no<=:upperSequence
                  AND (:unreadOnly=FALSE OR i.read_at IS NULL)
                  AND (:category IS NULL OR c.category=:category)
                """, params, Long.class);
        List<Row> rows = jdbc.query("""
                SELECT i.public_id,i.content_public_id,i.recipient_user_public_id,i.tenant_id,i.sequence_no,
                       c.notification_type,c.category,c.importance,c.title,c.body,c.target_type,c.target_public_id,
                       i.delivered_at,i.read_at
                FROM notification_inbox_items i
                JOIN notification_inbox_contents c ON c.public_id=i.content_public_id
                WHERE i.deleted=FALSE AND c.deleted=FALSE
                  AND i.recipient_user_public_id=:recipient
                  AND i.tenant_id IS NOT DISTINCT FROM CAST(:tenant AS UUID)
                  AND i.sequence_no<=:upperSequence
                  AND (:unreadOnly=FALSE OR i.read_at IS NULL)
                  AND (:category IS NULL OR c.category=:category)
                ORDER BY i.sequence_no DESC LIMIT :limit OFFSET :offset
                """, params, this::mapRow);
        return new Page(rows, total, currentReadRevision(recipient));
    }

    public Optional<Row> findItem(UUID itemPublicId, UUID recipient, UUID tenant) {
        return jdbc.query("""
                SELECT i.public_id,i.content_public_id,i.recipient_user_public_id,i.tenant_id,i.sequence_no,
                       c.notification_type,c.category,c.importance,c.title,c.body,c.target_type,c.target_public_id,
                       i.delivered_at,i.read_at
                FROM notification_inbox_items i
                JOIN notification_inbox_contents c ON c.public_id=i.content_public_id
                WHERE i.public_id=:item AND i.deleted=FALSE AND c.deleted=FALSE
                  AND i.recipient_user_public_id=:recipient
                  AND i.tenant_id IS NOT DISTINCT FROM CAST(:tenant AS UUID)
                """, ownerParams(recipient, tenant).addValue("item", itemPublicId), this::mapRow)
                .stream().findFirst();
    }

    public Optional<Row> markRead(UUID itemPublicId, UUID recipient, UUID tenant, Instant now) {
        Stream stream = lockOrCreateStream(recipient, tenant, true);
        int changed = jdbc.update("""
                UPDATE notification_inbox_items
                SET read_at=coalesce(read_at,:now),updated_at=:now
                WHERE public_id=:item AND deleted=FALSE AND recipient_user_public_id=:recipient
                  AND tenant_id IS NOT DISTINCT FROM CAST(:tenant AS UUID) AND read_at IS NULL
                """, ownerParams(recipient, tenant).addValue("item", itemPublicId).addValue("now", utc(now)));
        if (changed > 0) {
            incrementReadRevision(recipient);
        }
        return findItem(itemPublicId, recipient, tenant);
    }

    public UnreadSummary unreadCount(UUID recipient, UUID tenant) {
        Stream stream = lockOrCreateStream(recipient, tenant, true);
        long count = jdbc.queryForObject("""
                SELECT count(*) FROM notification_inbox_items
                WHERE deleted=FALSE AND recipient_user_public_id=:recipient
                  AND tenant_id IS NOT DISTINCT FROM CAST(:tenant AS UUID) AND read_at IS NULL
                """, ownerParams(recipient, tenant), Long.class);
        return new UnreadSummary(count, stream.readRevision());
    }

    public MarkAllResult markAll(UUID recipient, UUID tenant, Snapshot snapshot, Instant now) {
        Stream stream = lockOrCreateStream(recipient, tenant, true);
        if (snapshot != null && (!Objects.equals(snapshot.recipientUserPublicId(), recipient)
                || !Objects.equals(snapshot.tenantId(), tenant))) {
            throw scopeConflict();
        }
        long watermark = snapshot == null ? stream.lastSequence() : snapshot.upperSequence();
        int changed = jdbc.update("""
                UPDATE notification_inbox_items
                SET read_at=:now,updated_at=:now
                WHERE deleted=FALSE AND recipient_user_public_id=:recipient
                  AND tenant_id IS NOT DISTINCT FROM CAST(:tenant AS UUID)
                  AND sequence_no<=:watermark AND read_at IS NULL
                """, ownerParams(recipient, tenant).addValue("watermark", watermark).addValue("now", utc(now)));
        if (changed > 0) {
            incrementReadRevision(recipient);
        }
        return new MarkAllResult(changed, watermark, currentReadRevision(recipient));
    }

    private Stream lockOrCreateStream(UUID recipient, UUID tenant, boolean lock) {
        jdbc.update("""
                INSERT INTO notification_inbox_streams(public_id,recipient_user_public_id,tenant_id)
                VALUES (:publicId,:recipient,:tenant)
                ON CONFLICT (recipient_user_public_id) DO NOTHING
                """, ownerParams(recipient, tenant).addValue("publicId", UUID.randomUUID()));
        String suffix = lock ? " FOR UPDATE" : "";
        List<Stream> streams = jdbc.query("""
                SELECT tenant_id,last_sequence,read_revision
                FROM notification_inbox_streams
                WHERE recipient_user_public_id=:recipient
                """ + suffix, new MapSqlParameterSource().addValue("recipient", recipient), (rs, rowNum) ->
                new Stream(rs.getObject("tenant_id", UUID.class), rs.getLong("last_sequence"),
                        rs.getLong("read_revision")));
        Stream stream = streams.stream().findFirst().orElseThrow(this::scopeConflict);
        if (!Objects.equals(stream.tenantId(), tenant)) {
            throw scopeConflict();
        }
        return stream;
    }

    private long currentReadRevision(UUID recipient) {
        return jdbc.query("SELECT read_revision FROM notification_inbox_streams WHERE recipient_user_public_id=:recipient",
                new MapSqlParameterSource().addValue("recipient", recipient),
                (rs, rowNum) -> rs.getLong("read_revision")).stream().findFirst().orElse(0L);
    }

    private void incrementReadRevision(UUID recipient) {
        jdbc.update("""
                UPDATE notification_inbox_streams SET read_revision=read_revision+1,updated_at=CURRENT_TIMESTAMP
                WHERE recipient_user_public_id=:recipient
                """, new MapSqlParameterSource().addValue("recipient", recipient));
    }

    private MapSqlParameterSource ownerParams(UUID recipient, UUID tenant) {
        return new MapSqlParameterSource().addValue("recipient", recipient)
                .addValue("tenant", tenant, Types.OTHER);
    }

    private MapSqlParameterSource snapshotParams(UUID token, UUID recipient, UUID tenant, long upperSequence,
            long readRevision, Instant now, Instant expiresAt) {
        return ownerParams(recipient, tenant).addValue("token", token).addValue("upperSequence", upperSequence)
                .addValue("readRevision", readRevision).addValue("now", utc(now)).addValue("expiresAt", utc(expiresAt));
    }

    private Row mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Row(rs.getObject("public_id", UUID.class), rs.getObject("content_public_id", UUID.class),
                rs.getObject("recipient_user_public_id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getLong("sequence_no"), InboxNotificationType.valueOf(rs.getString("notification_type")),
                InboxCategory.valueOf(rs.getString("category")), InboxImportance.valueOf(rs.getString("importance")),
                rs.getString("title"), rs.getString("body"), InboxTargetType.valueOf(rs.getString("target_type")),
                rs.getObject("target_public_id", UUID.class), rs.getTimestamp("delivered_at").toInstant(),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant());
    }

    private static OffsetDateTime utc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private InboxNotificationException scopeConflict() {
        return new InboxNotificationException(HttpStatus.CONFLICT, InboxConstants.RECIPIENT_SCOPE_CONFLICT,
                InboxConstants.RECIPIENT_SCOPE_CONFLICT_MESSAGE);
    }

    public record Snapshot(UUID token, UUID recipientUserPublicId, UUID tenantId, long upperSequence,
            long readRevision, Instant expiresAt) { }

    public record Row(UUID publicId, UUID contentPublicId, UUID recipientUserPublicId, UUID tenantId, long sequenceNo,
            InboxNotificationType notificationType, InboxCategory category, InboxImportance importance, String title,
            String body, InboxTargetType targetType, UUID targetPublicId, Instant deliveredAt, Instant readAt) { }

    public record Page(List<Row> rows, long totalElements, long readRevision) { }

    public record UnreadSummary(long count, long readRevision) { }

    public record MarkAllResult(long markedCount, long watermark, long readRevision) { }

    private record Stream(UUID tenantId, long lastSequence, long readRevision) { }
}
