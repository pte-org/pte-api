package com.pte.chat.internal.service.session;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.exception.ChatSessionNotFoundException;
import com.pte.chat.internal.repository.ChatMessageRepository;
import com.pte.chat.internal.repository.ChatSessionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Conversations and their history. The client proposes a session id, so ownership is checked in two steps:
 * a read-only check before the charge (a foreign session is a 404 and nothing is written), and a claim after
 * the charge that inserts the row and reads the owner back, which settles two students racing for one id.
 * History and "has answers" count only OK messages; REFUSED and FAILED never become context.
 */
@Service
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatSessionService {

    private static final String COLUMNS = "id, created_at, session_id, student_id, question, answer, status";

    private static final RowMapper<ChatMessage> MESSAGE = (rs, n) -> {
        ChatMessage message = new ChatMessage();
        message.setId(rs.getLong("id"));
        message.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        message.setSessionId(rs.getObject("session_id", UUID.class));
        message.setStudentId(rs.getObject("student_id", UUID.class));
        message.setQuestion(rs.getString("question"));
        message.setAnswer(rs.getString("answer"));
        message.setStatus(rs.getString("status"));
        return message;
    };

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final NamedParameterJdbcTemplate jdbc;

    public ChatSessionService(ChatSessionRepository sessions, ChatMessageRepository messages,
                              NamedParameterJdbcTemplate jdbc) {
        this.sessions = sessions;
        this.messages = messages;
        this.jdbc = jdbc;
    }

    public void requireNotOwnedByOther(UUID sessionId, UUID studentId) {
        Optional<UUID> owner = owner(sessionId);
        if (owner.isPresent() && !owner.get().equals(studentId)) {
            throw new ChatSessionNotFoundException();
        }
    }

    public void claim(UUID sessionId, UUID studentId, Instant createdAt) {
        jdbc.update("INSERT INTO chat_session (session_id, student_id, created_at) VALUES (:id, :s, :c) "
                        + "ON CONFLICT (session_id) DO NOTHING",
                new MapSqlParameterSource("id", sessionId).addValue("s", studentId)
                        .addValue("c", Timestamp.from(createdAt)));
        if (!owner(sessionId).filter(studentId::equals).isPresent()) {
            throw new ChatSessionNotFoundException();
        }
    }

    public boolean hasAnswers(UUID sessionId, UUID studentId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM chat_message WHERE session_id = :id "
                        + "AND student_id = :s AND status = '" + ChatConstants.STATUS_OK + "' AND deleted = false)",
                new MapSqlParameterSource("id", sessionId).addValue("s", studentId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public List<ChatMessage> history(UUID sessionId, UUID studentId, int limit) {
        return lastMessages("status = '" + ChatConstants.STATUS_OK + "'", sessionId, studentId, limit);
    }

    public List<ChatMessage> displayMessages(UUID sessionId, UUID studentId, int limit) {
        return lastMessages("status IN ('" + ChatConstants.STATUS_OK + "', '" + ChatConstants.STATUS_REFUSED + "')",
                sessionId, studentId, limit);
    }

    /** Cache-served answers stored since {@code since}; they are free, so they get their own daily cap. */
    public int cacheHitsSince(UUID studentId, Instant since) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM chat_message WHERE student_id = :s "
                        + "AND from_cache = true AND deleted = false AND created_at >= :since",
                new MapSqlParameterSource("s", studentId).addValue("since", Timestamp.from(since)), Integer.class);
        return count == null ? 0 : count;
    }

    public Optional<UUID> latestSessionId(UUID studentId) {
        return jdbc.query("SELECT session_id FROM chat_message WHERE student_id = :s AND deleted = false "
                        + "ORDER BY created_at DESC, id DESC LIMIT 1",
                new MapSqlParameterSource("s", studentId), (rs, n) -> rs.getObject(1, UUID.class))
                .stream().findFirst();
    }

    private List<ChatMessage> lastMessages(String statusFilter, UUID sessionId, UUID studentId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM (SELECT " + COLUMNS + " FROM chat_message "
                        + "WHERE session_id = :id AND student_id = :s AND deleted = false AND " + statusFilter
                        + " ORDER BY created_at DESC, id DESC LIMIT :n) latest ORDER BY created_at ASC, id ASC",
                new MapSqlParameterSource("id", sessionId).addValue("s", studentId).addValue("n", limit), MESSAGE);
    }

    private Optional<UUID> owner(UUID sessionId) {
        return jdbc.query("SELECT student_id FROM chat_session WHERE session_id = :id",
                new MapSqlParameterSource("id", sessionId), (rs, n) -> rs.getObject(1, UUID.class))
                .stream().findFirst();
    }
}
