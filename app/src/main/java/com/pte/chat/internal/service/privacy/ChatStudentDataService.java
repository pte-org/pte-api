package com.pte.chat.internal.service.privacy;

import com.pte.chat.internal.constant.ChatConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/**
 * A student's own "delete my chats". It does what the nightly retention job does to old rows, at once and for
 * one student: the text goes, the link to the student and the session goes, the cost columns stay. The daily
 * counter is left alone on purpose, so deleting history cannot be used to win back the day's questions.
 */
@Service
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatStudentDataService {

    private static final String ERASE = "UPDATE chat_message SET question = '', answer = '', "
            + "student_id = :nil, session_id = :nil, updated_at = :now WHERE student_id = :s";
    private static final String DELETE_SESSIONS = "DELETE FROM chat_session WHERE student_id = :s";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public ChatStudentDataService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Returns how many messages were erased. */
    @Transactional
    public int deleteHistory(UUID studentId) {
        int erased = jdbc.update(ERASE, new MapSqlParameterSource("s", studentId)
                .addValue("nil", ChatConstants.NIL_UUID).addValue("now", Timestamp.from(clock.instant())));
        jdbc.update(DELETE_SESSIONS, new MapSqlParameterSource("s", studentId));
        return erased;
    }
}
