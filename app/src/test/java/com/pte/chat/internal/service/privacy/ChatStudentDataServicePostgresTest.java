package com.pte.chat.internal.service.privacy;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.repository.ChatMessageRepository;
import com.pte.chat.internal.repository.ChatSessionRepository;
import com.pte.chat.internal.service.session.ChatSessionService;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.ChatTestData;
import com.pte.chat.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A student's own "delete my chats": their text and their link to it go at once, other students are untouched. */
@ChatPostgresJpaTest
class ChatStudentDataServicePostgresTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private ChatSessionRepository sessions;
    @Autowired
    private ChatMessageRepository messages;

    private ChatStudentDataService service;
    private ChatTestData data;
    private UUID student;
    private UUID other;
    private UUID session;
    private UUID otherSession;

    @BeforeEach
    void setUp() {
        service = new ChatStudentDataService(jdbc, new MutableClock(NOW));
        data = new ChatTestData(jdbc);
        data.truncateAll();
        student = UUID.randomUUID();
        other = UUID.randomUUID();
        session = UUID.randomUUID();
        otherSession = UUID.randomUUID();
        data.insertSession(session, student, NOW.minusSeconds(600));
        data.insertSession(otherSession, other, NOW.minusSeconds(600));
        data.insertMessage(session, student, "my essay", "the answer", "OK", NOW.minusSeconds(500));
        data.insertMessage(session, student, "Weather?", "refusal", "REFUSED", NOW.minusSeconds(400));
        data.insertMessage(otherSession, other, "their question", "their answer", "OK", NOW.minusSeconds(300));
    }

    @Test
    void theStudentsMessagesLoseTheirTextAndTheirLinks() {
        assertThat(service.deleteHistory(student)).isEqualTo(2);

        assertThat(data.messageRowsForStudent(student)).isZero();
        assertThat(data.allMessages()).filteredOn(row -> ChatConstants.NIL_UUID.equals(row.get("student_id")))
                .hasSize(2)
                .allSatisfy(row -> assertThat(row).containsEntry("question", "").containsEntry("answer", "")
                        .containsEntry("session_id", ChatConstants.NIL_UUID));
    }

    @Test
    void costColumnsAndStatusStayForReports() {
        data.execute("UPDATE chat_message SET tokens_in = 11, model = 'm' WHERE question = 'my essay'");

        service.deleteHistory(student);

        assertThat(data.allMessages()).anySatisfy(row -> assertThat(row).containsEntry("status", "OK")
                .containsEntry("tokens_in", 11).containsEntry("model", "m"));
    }

    @Test
    void theStudentsSessionsAreDeleted() {
        service.deleteHistory(student);

        assertThat(data.sessionRows(session)).isZero();
    }

    @Test
    void anotherStudentKeepsEverything() {
        service.deleteHistory(student);

        assertThat(data.sessionRows(otherSession)).isEqualTo(1);
        assertThat(data.messageRowsForStudent(other)).isEqualTo(1);
        assertThat(data.messages(otherSession).getFirst()).containsEntry("question", "their question");
    }

    @Test
    void theDailyCounterStaysSoDeletingCannotBuyMoreQuestions() {
        data.setUsed(student, LocalDate.of(2026, 10, 29), 9);

        service.deleteHistory(student);

        assertThat(data.used(student, LocalDate.of(2026, 10, 29))).isEqualTo(9);
    }

    @Test
    void afterwardsTheStudentHasNoLatestSessionToReload() {
        service.deleteHistory(student);

        ChatSessionService sessionService = new ChatSessionService(sessions, messages, jdbc);
        assertThat(sessionService.latestSessionId(student)).isEmpty();
    }

    @Test
    void deletingTwiceIsHarmless() {
        service.deleteHistory(student);

        assertThat(service.deleteHistory(student)).isZero();
    }

    @Test
    void aStudentWithNoChatsDeletesNothing() {
        assertThat(service.deleteHistory(UUID.randomUUID())).isZero();
    }
}
