package com.pte.chat.internal.service.retention;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.ChatTestData;
import com.pte.chat.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content older than the retention window is blanked and anonymised in batches: the text goes, the link to the
 * student and the session goes, the cost columns stay. Young rows, sessions in use and the cache rules hold.
 */
@ChatPostgresJpaTest
class ChatRetentionServicePostgresTest {

    // 17:00 in Vietnam; twelve months earlier is 2025-10-29T10:00:00Z.
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2025-10-29T10:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ChatAiProperties properties;
    private ChatRetentionService service;
    private ChatTestData data;
    private UUID student;
    private UUID session;

    @BeforeEach
    void setUp() {
        properties = new ChatAiProperties();
        service = new ChatRetentionService(jdbc, properties, new MutableClock(NOW));
        data = new ChatTestData(jdbc);
        data.truncateAll();
        student = UUID.randomUUID();
        session = UUID.randomUUID();
        data.insertSession(session, student, CUTOFF.minusSeconds(3600));
    }

    private Map<String, Object> onlyRow() {
        return data.allMessages().getFirst();
    }

    @Test
    void theCutoffIsTwelveCalendarMonthsInVietnamTime() {
        assertThat(ChatRetentionService.contentCutoff(NOW, 12)).isEqualTo(CUTOFF);
    }

    @Test
    void aMessageJustOlderThanTheWindowLosesItsText() {
        data.insertMessage(session, student, "my essay", "the answer", "OK", CUTOFF.minusSeconds(1));

        assertThat(service.eraseExpiredContent(NOW)).isEqualTo(1);

        assertThat(onlyRow()).containsEntry("question", "").containsEntry("answer", "");
    }

    @Test
    void anErasedMessageIsNoLongerLinkedToTheStudentOrTheSession() {
        data.insertMessage(session, student, "my essay", "the answer", "OK", CUTOFF.minusSeconds(1));

        service.eraseExpiredContent(NOW);

        assertThat(onlyRow()).containsEntry("student_id", ChatConstants.NIL_UUID)
                .containsEntry("session_id", ChatConstants.NIL_UUID);
        assertThat(data.messageRowsForStudent(student)).isZero();
    }

    @Test
    void aMessageExactlyAtTheCutoffIsKeptWithItsOwner() {
        data.insertMessage(session, student, "my essay", "the answer", "OK", CUTOFF);

        assertThat(service.eraseExpiredContent(NOW)).isZero();

        assertThat(onlyRow()).containsEntry("question", "my essay").containsEntry("answer", "the answer")
                .containsEntry("student_id", student).containsEntry("session_id", session);
    }

    @Test
    void costColumnsSurviveTheErase() {
        data.insertMessage(session, student, "q", "a", "OK", CUTOFF.minusSeconds(60));
        data.execute("UPDATE chat_message SET tokens_in = 11, tokens_out = 22, model = 'm', prompt_version = 'v1', "
                + "finish_reason = 'STOP'");

        service.eraseExpiredContent(NOW);

        Map<String, Object> row = onlyRow();
        assertThat(row).containsEntry("status", "OK").containsEntry("tokens_in", 11).containsEntry("tokens_out", 22)
                .containsEntry("model", "m").containsEntry("prompt_version", "v1")
                .containsEntry("finish_reason", "STOP").containsEntry("from_cache", false);
    }

    @Test
    void everyOldRowIsErasedEvenWhenThereAreMoreThanOneBatch() {
        properties.setRetentionBatchSize(2);
        for (int i = 0; i < 5; i++) {
            data.insertMessage(session, student, "q" + i, "a" + i, "OK", CUTOFF.minusSeconds(100L + i));
        }
        data.insertMessage(session, student, "recent", "kept", "OK", NOW.minusSeconds(60));

        assertThat(service.eraseExpiredContent(NOW)).isEqualTo(5);
        assertThat(service.eraseExpiredContent(NOW)).isZero();

        assertThat(data.allMessages()).filteredOn(row -> "".equals(row.get("question"))).hasSize(5);
        assertThat(data.allMessages()).filteredOn(row -> "recent".equals(row.get("question"))).hasSize(1);
    }

    @Test
    void aRowThatIsAlreadyBlankAndAnonymousIsNotCountedAgain() {
        data.insertMessage(ChatConstants.NIL_UUID, ChatConstants.NIL_UUID, "", "", "FAILED", CUTOFF.minusSeconds(60));

        assertThat(service.eraseExpiredContent(NOW)).isZero();
    }

    @Test
    void aBlankRowThatStillHasAnOwnerIsAnonymised() {
        data.insertMessage(session, student, "", "", "FAILED", CUTOFF.minusSeconds(60));

        assertThat(service.eraseExpiredContent(NOW)).isEqualTo(1);

        assertThat(onlyRow()).containsEntry("student_id", ChatConstants.NIL_UUID);
    }

    @Test
    void aShorterWindowErasesYoungerMessages() {
        properties.setRetentionContentMonths(6);
        data.insertMessage(session, student, "nine months old", "a", "OK", Instant.parse("2026-01-29T10:00:00Z"));

        assertThat(service.eraseExpiredContent(NOW)).isEqualTo(1);
    }

    @Test
    void anOldSessionWithNoMessagesLeftIsDeleted() {
        data.insertMessage(session, student, "old", "a", "OK", CUTOFF.minusSeconds(60));
        service.eraseExpiredContent(NOW);

        assertThat(service.deleteOrphanedSessions(NOW)).isEqualTo(1);

        assertThat(data.sessionRows(session)).isZero();
    }

    @Test
    void aSessionThatStillHasRecentMessagesIsKept() {
        data.insertMessage(session, student, "old", "a", "OK", CUTOFF.minusSeconds(60));
        data.insertMessage(session, student, "recent", "a", "OK", NOW.minusSeconds(60));
        service.eraseExpiredContent(NOW);

        assertThat(service.deleteOrphanedSessions(NOW)).isZero();

        assertThat(data.sessionRows(session)).isEqualTo(1);
    }

    @Test
    void aYoungSessionWithNoMessagesYetIsKept() {
        UUID fresh = UUID.randomUUID();
        data.insertSession(fresh, student, NOW.minusSeconds(60));

        service.deleteOrphanedSessions(NOW);

        assertThat(data.sessionRows(fresh)).isEqualTo(1);
    }

    @Test
    void anExpiredCacheEntryIsDeletedButFreshAndPermanentOnesStay() {
        data.insertCache("expired", "a", NOW.minusSeconds(1));
        data.insertCache("due-now", "a", NOW);
        data.insertCache("fresh", "a", NOW.plusSeconds(60));
        data.insertCache("forever", "a", null);

        assertThat(service.deleteExpiredCache(NOW)).isEqualTo(2);

        assertThat(data.cacheRows("expired")).isZero();
        assertThat(data.cacheRows("due-now")).isZero();
        assertThat(data.cacheRows("fresh")).isEqualTo(1);
        assertThat(data.cacheRows("forever")).isEqualTo(1);
    }

    @Test
    void theScheduledRunDoesAllThreeJobs() {
        data.insertMessage(session, student, "old", "a", "OK", CUTOFF.minusSeconds(1));
        data.insertCache("expired", "a", NOW.minusSeconds(1));

        service.purge();

        assertThat(onlyRow()).containsEntry("question", "").containsEntry("student_id", ChatConstants.NIL_UUID);
        assertThat(data.sessionRows(session)).isZero();
        assertThat(data.cacheRows("expired")).isZero();
    }
}
