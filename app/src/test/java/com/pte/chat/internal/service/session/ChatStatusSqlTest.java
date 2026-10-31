package com.pte.chat.internal.service.session;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.repository.ChatMessageRepository;
import com.pte.chat.internal.repository.ChatSessionRepository;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.ChatTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status filters of {@link ChatSessionService} are built from {@code ChatConstants.STATUS_*}; one row per
 * status proves they still select exactly the statuses the V7 CHECK constraint stores.
 */
@ChatPostgresJpaTest
class ChatStatusSqlTest {

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

    @BeforeEach
    void setUp() {
        service = new ChatSessionService(sessions, messages, jdbc);
        data = new ChatTestData(jdbc);
        student = UUID.randomUUID();
    }

    private UUID sessionWithOneRowPerStatus() {
        UUID session = UUID.randomUUID();
        data.insertMessage(session, student, "ok", "a", ChatConstants.STATUS_OK, T0);
        data.insertMessage(session, student, "refused", "a", ChatConstants.STATUS_REFUSED, T0.plus(1, ChronoUnit.MINUTES));
        data.insertMessage(session, student, "failed", "", ChatConstants.STATUS_FAILED, T0.plus(2, ChronoUnit.MINUTES));
        return session;
    }

    private static List<String> questions(List<ChatMessage> list) {
        return list.stream().map(ChatMessage::getQuestion).toList();
    }

    @Test
    void theStatusConstantsAreTheValuesTheDatabaseStores() {
        assertThat(ChatConstants.STATUS_OK).isEqualTo("OK");
        assertThat(ChatConstants.STATUS_REFUSED).isEqualTo("REFUSED");
        assertThat(ChatConstants.STATUS_FAILED).isEqualTo("FAILED");
    }

    @Test
    void historyContainsOnlyTheOkRow() {
        UUID session = sessionWithOneRowPerStatus();

        assertThat(questions(service.history(session, student, 10))).containsExactly("ok");
    }

    @Test
    void displayMessagesContainsTheOkAndTheRefusedRowButNotTheFailedOne() {
        UUID session = sessionWithOneRowPerStatus();

        assertThat(questions(service.displayMessages(session, student, 10))).containsExactly("ok", "refused");
    }

    @Test
    void displayMessagesKeepsOldestFirstAcrossTheTwoStatuses() {
        UUID session = UUID.randomUUID();
        data.insertMessage(session, student, "first refused", "a", ChatConstants.STATUS_REFUSED, T0);
        data.insertMessage(session, student, "then ok", "a", ChatConstants.STATUS_OK, T0.plusSeconds(60));
        data.insertMessage(session, student, "then failed", "", ChatConstants.STATUS_FAILED, T0.plusSeconds(120));
        data.insertMessage(session, student, "last refused", "a", ChatConstants.STATUS_REFUSED, T0.plusSeconds(180));

        assertThat(questions(service.displayMessages(session, student, 10)))
                .containsExactly("first refused", "then ok", "last refused");
        assertThat(questions(service.displayMessages(session, student, 2))).containsExactly("then ok", "last refused");
    }

    @ParameterizedTest
    @CsvSource({"OK,true", "REFUSED,false", "FAILED,false"})
    void hasAnswersIsTrueOnlyForAnOkRow(String status, boolean expected) {
        UUID session = UUID.randomUUID();
        data.insertMessage(session, student, "only row", status.equals("FAILED") ? "" : "a", status, T0);

        assertThat(service.hasAnswers(session, student)).isEqualTo(expected);
    }

    @Test
    void hasAnswersIsTrueForTheMixedSessionBecauseOfItsOkRow() {
        assertThat(service.hasAnswers(sessionWithOneRowPerStatus(), student)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"OK,1,1", "REFUSED,0,1", "FAILED,0,0"})
    void eachStatusIsSelectedByExactlyTheFiltersThatShouldSeeIt(String status, int inHistory, int inDisplay) {
        UUID session = UUID.randomUUID();
        data.insertMessage(session, student, "only row", status.equals("FAILED") ? "" : "a", status, T0);

        assertThat(service.history(session, student, 10)).hasSize(inHistory);
        assertThat(service.displayMessages(session, student, 10)).hasSize(inDisplay);
    }
}
