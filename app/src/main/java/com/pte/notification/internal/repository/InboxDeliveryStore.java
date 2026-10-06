package com.pte.notification.internal.repository;

import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.InboxRecipient;
import com.pte.notification.domain.enums.InboxDeliveryStatus;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.exception.InboxNotificationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL atomic operations not expressible as a check-then-save JPA workflow. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class InboxDeliveryStore {
    private final NamedParameterJdbcTemplate jdbc;

    public InboxDeliveryStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID append(InboxNotificationRequested event, Instant now) {
        String hash = fingerprint(event);
        MapSqlParameterSource params = at(now).addValue("eventKey", event.eventKey())
                .addValue("version", event.schemaVersion()).addValue("hash", hash)
                .addValue("type", event.type().name()).addValue("category", event.category().name())
                .addValue("importance", event.importance().name()).addValue("title", event.title())
                .addValue("body", event.body()).addValue("targetType", event.targetType().name())
                .addValue("targetId", event.targetPublicId());
        jdbc.update("""
                INSERT INTO notification_inbox_contents
                  (event_key,schema_version,payload_hash,notification_type,category,importance,title,body,
                   target_type,target_public_id,created_at,updated_at)
                VALUES (:eventKey,:version,:hash,:type,:category,:importance,:title,:body,:targetType,:targetId,:now,:now)
                ON CONFLICT (event_key) DO NOTHING
                """, params);
        List<UUID> contents = jdbc.query("""
                SELECT public_id FROM notification_inbox_contents
                WHERE event_key=:eventKey AND payload_hash=:hash AND deleted=FALSE
                """, params, (rs, row) -> rs.getObject("public_id", UUID.class));
        if (contents.isEmpty()) {
            throw new InboxNotificationException(HttpStatus.CONFLICT, InboxConstants.EVENT_CONFLICT,
                    InboxConstants.EVENT_CONFLICT_MESSAGE);
        }
        // Audience is included in the fingerprint: a replay cannot expand a frozen publication.
        MapSqlParameterSource[] audience = event.recipients().stream().map(recipient -> at(now)
                .addValue("contentId", contents.getFirst()).addValue("recipient", recipient.userPublicId())
                .addValue("tenant", recipient.tenantId(), Types.OTHER)).toArray(MapSqlParameterSource[]::new);
        if (audience.length > 0) {
            jdbc.batchUpdate("""
                    INSERT INTO notification_inbox_deliveries
                      (content_public_id,recipient_user_public_id,tenant_id,next_attempt_at,created_at,updated_at)
                    VALUES (:contentId,:recipient,:tenant,:now,:now,:now)
                    ON CONFLICT (content_public_id,recipient_user_public_id) DO NOTHING
                    """, audience);
        }
        return contents.getFirst();
    }

    public int retryFailedForContent(UUID contentPublicId, Instant now) {
        return jdbc.update("""
                UPDATE notification_inbox_deliveries SET status='PENDING',attempts=0,next_attempt_at=:now,
                  last_failure_code=NULL,completed_at=NULL,updated_at=:now
                WHERE content_public_id=:contentId AND status='FAILED' AND deleted=FALSE
                """, at(now).addValue("contentId", contentPublicId));
    }

    /** Suppresses only pending reminders from an obsolete schedule; delivered history is retained. */
    public int suppressPendingSessionReminders(UUID sessionPublicId, String keepEventKey, Instant now) {
        return jdbc.update("""
                UPDATE notification_inbox_deliveries d
                SET status='SUPPRESSED',claim_token=NULL,lease_until=NULL,completed_at=:now,updated_at=:now
                FROM notification_inbox_contents c
                WHERE d.content_public_id=c.public_id AND d.deleted=FALSE AND c.deleted=FALSE
                  AND d.status='PENDING' AND c.notification_type='SESSION_CLOSING_SOON'
                  AND c.target_type='SESSION' AND c.target_public_id=:sessionId
                  AND (:keepEventKey IS NULL OR c.event_key<>:keepEventKey)
                """, at(now).addValue("sessionId", sessionPublicId)
                .addValue("keepEventKey", keepEventKey, Types.VARCHAR));
    }

    /** Returns bounded session targets whose pending reminder may have become stale after rescheduling. */
    public List<UUID> findPendingSessionReminderTargets() {
        return jdbc.query("""
                SELECT DISTINCT c.target_public_id
                FROM notification_inbox_deliveries d
                JOIN notification_inbox_contents c ON c.public_id=d.content_public_id
                WHERE d.deleted=FALSE AND c.deleted=FALSE AND d.status='PENDING'
                  AND c.notification_type='SESSION_CLOSING_SOON' AND c.target_type='SESSION'
                ORDER BY c.target_public_id
                LIMIT :limit
                """, new MapSqlParameterSource("limit", InboxConstants.RECOVERY_BATCH_LIMIT),
                (rs, row) -> rs.getObject("target_public_id", UUID.class));
    }

    public List<InboxDeliveryClaim> claimBatch(Instant now, Instant leaseUntil, int limit, int maxAttempts) {
        return jdbc.query("""
                WITH due AS (
                  SELECT id FROM notification_inbox_deliveries
                  WHERE deleted=FALSE AND attempts < :maxAttempts
                    AND ((status='PENDING' AND next_attempt_at<=:now)
                         OR (status='PROCESSING' AND lease_until<=:now))
                  ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED
                )
                UPDATE notification_inbox_deliveries d
                SET status='PROCESSING',attempts=d.attempts+1,claim_token=gen_random_uuid(),
                    lease_until=:lease,updated_at=:now
                FROM due WHERE d.id=due.id
                RETURNING d.public_id,d.claim_token,d.attempts
                """, at(now).addValue("lease", utc(leaseUntil)).addValue("limit", limit)
                .addValue("maxAttempts", maxAttempts), (rs, row) -> new InboxDeliveryClaim(
                        rs.getObject("public_id", UUID.class), rs.getObject("claim_token", UUID.class), rs.getInt("attempts")));
    }

    public int exhaustExpired(Instant now, int maxAttempts) {
        return jdbc.update("""
                WITH exhausted AS (
                  SELECT id FROM notification_inbox_deliveries
                  WHERE deleted=FALSE AND status='PROCESSING' AND lease_until<=:now AND attempts>=:maxAttempts
                  ORDER BY lease_until,id LIMIT :limit FOR UPDATE SKIP LOCKED
                )
                UPDATE notification_inbox_deliveries d SET status='FAILED',claim_token=NULL,lease_until=NULL,
                  last_failure_code=:failure,completed_at=:now,updated_at=:now
                FROM exhausted WHERE d.id=exhausted.id
                """, at(now).addValue("maxAttempts", maxAttempts).addValue("failure", InboxConstants.LEASE_EXHAUSTED)
                .addValue("limit", InboxConstants.RECOVERY_BATCH_LIMIT));
    }

    public Optional<InboxDeliveryRecord> findClaim(InboxDeliveryClaim claim, Instant now) {
        return jdbc.query("""
                SELECT d.public_id,d.content_public_id,d.recipient_user_public_id,d.tenant_id,c.notification_type
                FROM notification_inbox_deliveries d JOIN notification_inbox_contents c ON c.public_id=d.content_public_id
                WHERE d.public_id=:id AND d.claim_token=:token AND d.status='PROCESSING'
                  AND d.lease_until>:now AND d.deleted=FALSE AND c.deleted=FALSE
                """, claimed(claim, now), (rs, row) -> new InboxDeliveryRecord(rs.getObject("public_id", UUID.class),
                rs.getObject("content_public_id", UUID.class), rs.getObject("recipient_user_public_id", UUID.class),
                rs.getObject("tenant_id", UUID.class), InboxNotificationType.valueOf(rs.getString("notification_type"))))
                .stream().findFirst();
    }

    public boolean lockClaim(InboxDeliveryClaim claim, Instant now) {
        return !jdbc.query("""
                SELECT id FROM notification_inbox_deliveries
                WHERE public_id=:id AND claim_token=:token AND status='PROCESSING'
                  AND lease_until>:now AND deleted=FALSE FOR UPDATE
                """, claimed(claim, now), (rs, row) -> rs.getLong("id")).isEmpty();
    }

    /** Caller holds recipient eligibility guards, then the delivery fence, then this stream lock. */
    public void deliver(InboxDeliveryRecord record, InboxDeliveryClaim claim, Instant now) {
        MapSqlParameterSource params = claimed(claim, now).addValue("recipient", record.recipientPublicId())
                .addValue("tenant", record.tenantId(), Types.OTHER).addValue("contentId", record.contentPublicId());
        jdbc.update("""
                INSERT INTO notification_inbox_streams(recipient_user_public_id,tenant_id,created_at,updated_at)
                VALUES (:recipient,:tenant,:now,:now) ON CONFLICT (recipient_user_public_id) DO NOTHING
                """, params);
        List<Long> sequences = jdbc.query("""
                SELECT last_sequence FROM notification_inbox_streams
                WHERE recipient_user_public_id=:recipient AND tenant_id IS NOT DISTINCT FROM CAST(:tenant AS UUID)
                FOR UPDATE
                """, params, (rs, row) -> rs.getLong("last_sequence"));
        if (sequences.isEmpty()) {
            throw new InboxNotificationException(HttpStatus.CONFLICT, InboxConstants.RECIPIENT_SCOPE_CONFLICT,
                    InboxConstants.RECIPIENT_SCOPE_CONFLICT_MESSAGE);
        }
        params.addValue("sequence", sequences.getFirst() + 1);
        int inserted = jdbc.update("""
                INSERT INTO notification_inbox_items(delivery_public_id,content_public_id,recipient_user_public_id,
                  tenant_id,sequence_no,delivered_at,created_at,updated_at)
                SELECT d.public_id,d.content_public_id,d.recipient_user_public_id,d.tenant_id,:sequence,:now,:now,:now
                FROM notification_inbox_deliveries d
                WHERE d.public_id=:id AND d.claim_token=:token AND d.status='PROCESSING' AND d.lease_until>:now
                  AND d.content_public_id=:contentId AND d.recipient_user_public_id=:recipient
                  AND d.tenant_id IS NOT DISTINCT FROM CAST(:tenant AS UUID)
                ON CONFLICT (delivery_public_id) DO NOTHING
                """, params);
        if (inserted == 1) {
            jdbc.update("""
                    UPDATE notification_inbox_streams SET last_sequence=:sequence,updated_at=:now
                    WHERE recipient_user_public_id=:recipient
                    """, params);
        } else if (jdbc.queryForObject("""
                SELECT count(*) FROM notification_inbox_items WHERE delivery_public_id=:id
                  AND content_public_id=:contentId AND recipient_user_public_id=:recipient
                  AND tenant_id IS NOT DISTINCT FROM CAST(:tenant AS UUID)
                """, params, Long.class) == 0) { throw claimLost(); }
        terminal(claim, now, InboxDeliveryStatus.DELIVERED);
    }

    public void suppress(InboxDeliveryClaim claim, Instant now) { terminal(claim, now, InboxDeliveryStatus.SUPPRESSED); }

    public void fail(InboxDeliveryClaim claim, Instant now, Instant nextAttemptAt, InboxDeliveryStatus status, String failureCode) {
        jdbc.update("""
                UPDATE notification_inbox_deliveries SET status=:status,claim_token=NULL,lease_until=NULL,
                  last_failure_code=:failure,next_attempt_at=:next,completed_at=:completed,updated_at=:now
                WHERE public_id=:id AND claim_token=:token AND status='PROCESSING' AND lease_until>:now
                """, claimed(claim, now).addValue("status", status.name()).addValue("failure", failureCode)
                .addValue("next", utc(nextAttemptAt)).addValue("completed", status == InboxDeliveryStatus.FAILED ? utc(now) : null,
                        Types.TIMESTAMP_WITH_TIMEZONE));
    }

    /** Internal operation only; the later admin endpoint must authorize and audit its invocation. */
    public boolean retryFailed(UUID publicId, Instant now) {
        return jdbc.update("""
                UPDATE notification_inbox_deliveries SET status='PENDING',attempts=0,next_attempt_at=:now,
                  last_failure_code=NULL,completed_at=NULL,updated_at=:now
                WHERE public_id=:id AND status='FAILED' AND deleted=FALSE
                """, at(now).addValue("id", publicId)) == 1;
    }

    public long countByStatus(InboxDeliveryStatus status) {
        return jdbc.queryForObject("SELECT count(*) FROM notification_inbox_deliveries WHERE status=:status AND deleted=FALSE",
                new MapSqlParameterSource("status", status.name()), Long.class);
    }

    private void terminal(InboxDeliveryClaim claim, Instant now, InboxDeliveryStatus status) {
        int updated = jdbc.update("""
                UPDATE notification_inbox_deliveries SET status=:status,claim_token=NULL,lease_until=NULL,
                  last_failure_code=NULL,completed_at=:now,updated_at=:now
                WHERE public_id=:id AND claim_token=:token AND status='PROCESSING' AND lease_until>:now
                """, claimed(claim, now).addValue("status", status.name()));
        if (updated != 1) { throw claimLost(); }
    }

    private static InboxNotificationException claimLost() {
        return new InboxNotificationException(HttpStatus.CONFLICT, InboxConstants.CLAIM_LOST, InboxConstants.CLAIM_LOST_MESSAGE);
    }

    private static MapSqlParameterSource claimed(InboxDeliveryClaim claim, Instant now) {
        return at(now).addValue("id", claim.publicId()).addValue("token", claim.token());
    }
    private static MapSqlParameterSource at(Instant now) { return new MapSqlParameterSource("now", utc(now)); }
    private static OffsetDateTime utc(Instant value) { return value.atOffset(ZoneOffset.UTC); }

    private static String fingerprint(InboxNotificationRequested event) {
        StringBuilder input = new StringBuilder();
        for (String value : List.of(Integer.toString(event.schemaVersion()), event.eventKey(), event.type().name(),
                event.category().name(), event.importance().name(), event.title(), event.body(), event.targetType().name(),
                event.targetPublicId().toString())) {
            input.append(value.length()).append(':').append(value);
        }
        for (InboxRecipient recipient : event.recipients()) {
            input.append(recipient.userPublicId()).append(':').append(recipient.tenantId()).append(';');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(InboxConstants.NON_RETRYABLE_FAILURE, impossible);
        }
    }
}
