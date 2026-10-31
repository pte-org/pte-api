package com.pte.chat.internal.service.session;

import com.pte.chat.internal.repository.ChatMessageRepository;
import com.pte.chat.internal.repository.ChatSessionRepository;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.ChatTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link ChatSessionService#cacheHitsSince}: only this student's live, cache-served rows from the window on. */
@ChatPostgresJpaTest
class ChatSessionServiceCacheHitsPostgresTest {

    private static final Instant SINCE = Instant.parse("2026-10-27T17:00:00Z");

    @Autowired
    private ChatSessionRepository sessions;
    @Autowired
    private ChatMessageRepository messages;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ChatSessionService service;
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        service = new ChatSessionService(sessions, messages, jdbc);
        new ChatTestData(jdbc).truncateAll();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    private void row(UUID student, Instant createdAt, boolean fromCache, boolean deleted, String status) {
        jdbc.update("INSERT INTO chat_message (public_id, created_at, updated_at, deleted, session_id, student_id, "
                        + "tenant_id, question, answer, status, on_topic, from_cache) "
                        + "VALUES (:pid, :c, :c, :del, :sid, :stu, :tenant, 'q', 'a', :st, true, :fc)",
                new MapSqlParameterSource("pid", UUID.randomUUID()).addValue("c", Timestamp.from(createdAt))
                        .addValue("del", deleted).addValue("sid", UUID.randomUUID()).addValue("stu", student)
                        .addValue("tenant", UUID.randomUUID()).addValue("st", status).addValue("fc", fromCache));
    }

    @Test
    void aStudentWithoutAnyMessageHasZeroCacheHits() {
        assertThat(service.cacheHitsSince(alice, SINCE)).isZero();
    }

    @Test
    void countsOnlyCacheServedRows() {
        row(alice, SINCE.plusSeconds(10), true, false, "OK");
        row(alice, SINCE.plusSeconds(20), true, false, "OK");
        row(alice, SINCE.plusSeconds(30), false, false, "OK");
        row(alice, SINCE.plusSeconds(40), false, false, "REFUSED");

        assertThat(service.cacheHitsSince(alice, SINCE)).isEqualTo(2);
    }

    @Test
    void countsOnlyRowsFromTheWindowStartInclusive() {
        row(alice, SINCE.minusSeconds(1), true, false, "OK");
        row(alice, SINCE, true, false, "OK");
        row(alice, SINCE.plusSeconds(1), true, false, "OK");

        assertThat(service.cacheHitsSince(alice, SINCE)).isEqualTo(2);
        assertThat(service.cacheHitsSince(alice, SINCE.plusSeconds(1))).isEqualTo(1);
        assertThat(service.cacheHitsSince(alice, SINCE.plusSeconds(2))).isZero();
        assertThat(service.cacheHitsSince(alice, SINCE.minusSeconds(1))).isEqualTo(3);
    }

    @Test
    void ignoresSoftDeletedRows() {
        row(alice, SINCE.plusSeconds(10), true, true, "OK");
        row(alice, SINCE.plusSeconds(20), true, false, "OK");

        assertThat(service.cacheHitsSince(alice, SINCE)).isEqualTo(1);
    }

    @Test
    void ignoresOtherStudents() {
        row(bob, SINCE.plusSeconds(10), true, false, "OK");
        row(bob, SINCE.plusSeconds(20), true, false, "OK");
        row(alice, SINCE.plusSeconds(30), true, false, "OK");

        assertThat(service.cacheHitsSince(alice, SINCE)).isEqualTo(1);
        assertThat(service.cacheHitsSince(bob, SINCE)).isEqualTo(2);
    }
}
