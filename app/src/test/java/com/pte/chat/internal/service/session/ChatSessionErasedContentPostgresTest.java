package com.pte.chat.internal.service.session;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.repository.ChatMessageRepository;
import com.pte.chat.internal.repository.ChatSessionRepository;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.ChatTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Rows whose text the retention job blanked must never show up as empty bubbles or as AI context. */
@ChatPostgresJpaTest
class ChatSessionErasedContentPostgresTest {

    private static final Instant T0 = Instant.parse("2026-10-28T03:00:00Z");

    @Autowired
    private ChatSessionRepository sessions;
    @Autowired
    private ChatMessageRepository messages;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ChatSessionService service;
    private ChatTestData data;
    private UUID student;
    private UUID session;

    @BeforeEach
    void setUp() {
        service = new ChatSessionService(sessions, messages, jdbc);
        data = new ChatTestData(jdbc);
        student = UUID.randomUUID();
        session = UUID.randomUUID();
        data.insertSession(session, student, T0);
    }

    private void message(String question, String answer, String status, int minute) {
        data.insertMessage(session, student, question, answer, status, T0.plus(minute, ChronoUnit.MINUTES));
    }

    private static List<String> questions(List<ChatMessage> list) {
        return list.stream().map(ChatMessage::getQuestion).toList();
    }

    @Test
    void historySkipsBlankedRows() {
        message("", "", "OK", 1);
        message("kept", "answer", "OK", 2);

        assertThat(questions(service.history(session, student, 6))).containsExactly("kept");
    }

    @Test
    void displayMessagesSkipBlankedRowsOfBothStatuses() {
        message("", "", "OK", 1);
        message("", "", "REFUSED", 2);
        message("kept", "answer", "OK", 3);

        assertThat(questions(service.displayMessages(session, student, 50))).containsExactly("kept");
    }

    @Test
    void theLimitCountsOnlyRowsThatCanBeShown() {
        message("old one", "a", "OK", 1);
        message("", "", "OK", 2);
        message("", "", "OK", 3);

        assertThat(questions(service.history(session, student, 1))).containsExactly("old one");
    }

    @Test
    void aSessionOfOnlyBlankedRowsHasNoAnswers() {
        message("", "", "OK", 1);

        assertThat(service.hasAnswers(session, student)).isFalse();
    }

    @Test
    void theLatestSessionSkipsOneWhoseTextWasErased() {
        UUID older = UUID.randomUUID();
        data.insertSession(older, student, T0);
        data.insertMessage(older, student, "still here", "a", "OK", T0.minus(1, ChronoUnit.DAYS));
        message("", "", "OK", 5);

        assertThat(service.latestSessionId(student)).contains(older);
    }

    @Test
    void anAnonymisedRowIsInvisibleToEveryStudent() {
        data.insertMessage(ChatConstants.NIL_UUID, ChatConstants.NIL_UUID, "", "", "OK", T0);

        assertThat(service.latestSessionId(student)).isEmpty();
        assertThat(service.history(ChatConstants.NIL_UUID, student, 6)).isEmpty();
    }

    @Test
    void aStudentWhoseEverythingWasErasedHasNoLatestSession() {
        message("", "", "OK", 1);

        assertThat(service.latestSessionId(student)).isEmpty();
    }
}
