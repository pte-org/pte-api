package com.pte.chat.internal.service.retention;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Nightly clean-up that keeps the promises in the privacy hand-off. After {@code chat.ai.retention-content-months}
 * a message loses its text and its link to the student and the session, so what remains (status, token counts,
 * model, time) is anonymous and still adds up for cost reports. Sessions left with no message are deleted, and
 * cached answers expire after {@code chat.ai.retention-cache-days}. The work runs in small batches, each its own
 * statement, so it never holds a long lock; it never logs any text.
 */
@Service
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatRetentionService {

    private static final Logger log = LoggerFactory.getLogger(ChatRetentionService.class);

    private static final String ERASE_BATCH = "UPDATE chat_message SET question = '', answer = '', "
            + "student_id = :nil, session_id = :nil, updated_at = :now "
            + "WHERE id IN (SELECT id FROM chat_message WHERE created_at < :cutoff AND "
            + "(question <> '' OR answer <> '' OR student_id <> :nil OR session_id <> :nil) "
            + "ORDER BY id LIMIT :batch)";
    private static final String DELETE_ORPHANED_SESSIONS = "DELETE FROM chat_session s WHERE s.created_at < :cutoff "
            + "AND NOT EXISTS (SELECT 1 FROM chat_message m WHERE m.session_id = s.session_id)";
    private static final String DELETE_EXPIRED_CACHE =
            "DELETE FROM chat_answer_cache WHERE expires_at IS NOT NULL AND expires_at <= :now";

    private final NamedParameterJdbcTemplate jdbc;
    private final ChatAiProperties properties;
    private final Clock clock;

    public ChatRetentionService(NamedParameterJdbcTemplate jdbc, ChatAiProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = ChatConstants.RETENTION_CRON_PROPERTY, zone = ChatConstants.ZONE_ID)
    public void purge() {
        Instant now = clock.instant();
        int erased = eraseExpiredContent(now);
        int sessions = deleteOrphanedSessions(now);
        int cached = deleteExpiredCache(now);
        log.info("Chat retention anonymised {} messages, deleted {} sessions and {} cached answers",
                erased, sessions, cached);
    }

    /** Blanks and anonymises every message older than the window; returns how many rows were changed. */
    public int eraseExpiredContent(Instant now) {
        Timestamp cutoff = Timestamp.from(contentCutoff(now, properties.getRetentionContentMonths()));
        int batch = properties.getRetentionBatchSize();
        int total = 0;
        int changed;
        do {
            changed = jdbc.update(ERASE_BATCH, new MapSqlParameterSource("now", Timestamp.from(now))
                    .addValue("cutoff", cutoff).addValue("batch", batch).addValue("nil", ChatConstants.NIL_UUID));
            total += changed;
        } while (changed == batch);
        return total;
    }

    /** Sessions older than the window that no message points to any more (their messages were anonymised). */
    public int deleteOrphanedSessions(Instant now) {
        Timestamp cutoff = Timestamp.from(contentCutoff(now, properties.getRetentionContentMonths()));
        return jdbc.update(DELETE_ORPHANED_SESSIONS, new MapSqlParameterSource("cutoff", cutoff));
    }

    public int deleteExpiredCache(Instant now) {
        return jdbc.update(DELETE_EXPIRED_CACHE, new MapSqlParameterSource("now", Timestamp.from(now)));
    }

    /** Calendar months in Vietnam time, the zone the quota day and the policy text use. */
    static Instant contentCutoff(Instant now, int months) {
        return ZonedDateTime.ofInstant(now, ChatConstants.ZONE).minusMonths(months).toInstant();
    }
}
