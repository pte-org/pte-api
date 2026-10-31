package com.pte.chat.internal.service.session;

import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.exception.ChatSessionNotFoundException;
import com.pte.chat.internal.repository.ChatMessageRepository;
import com.pte.chat.internal.repository.ChatSessionRepository;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.ChatTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Session ownership, "does this session already have an answer" and the history queries, on the real schema.
 * Rows are written with plain SQL so the order of events (ids and timestamps) is exactly what each test says.
 */
@ChatPostgresJpaTest
class ChatSessionServicePostgresTest {

    private static final Instant T0 = Instant.parse("2026-10-28T03:00:00Z");

    @Autowired
    private ChatSessionRepository sessions;
    @Autowired
    private ChatMessageRepository messages;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ChatSessionService service;
    private ChatTestData data;
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        service = new ChatSessionService(sessions, messages, jdbc);
        data = new ChatTestData(jdbc);
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    private void message(UUID session, UUID student, String question, String status, int minute) {
        data.insertMessage(session, student, question, "answer to " + question, status, T0.plus(minute, ChronoUnit.MINUTES));
    }

    private static List<String> questions(List<ChatMessage> list) {
        return list.stream().map(ChatMessage::getQuestion).toList();
    }

    // ---- requireNotOwnedByOther --------------------------------------------------------------------------

    @Test
    void anUnknownSessionPassesTheOwnerCheckAndWritesNothing() {
        UUID session = UUID.randomUUID();

        assertThatCode(() -> service.requireNotOwnedByOther(session, alice)).doesNotThrowAnyException();

        assertThat(data.sessionRows(session)).isZero();
    }

    @Test
    void myOwnSessionPassesTheOwnerCheck() {
        UUID session = UUID.randomUUID();
        data.insertSession(session, alice, T0);

        assertThatCode(() -> service.requireNotOwnedByOther(session, alice)).doesNotThrowAnyException();
    }

    @Test
    void anotherStudentsSessionIsNotFoundAndIsLeftUntouched() {
        UUID session = UUID.randomUUID();
        data.insertSession(session, alice, T0);

        assertThatThrownBy(() -> service.requireNotOwnedByOther(session, bob))
                .isInstanceOf(ChatSessionNotFoundException.class);

        assertThat(data.sessionOwner(session)).contains(alice);
        assertThat(data.sessionRows(session)).isEqualTo(1);
    }

    // ---- claim -------------------------------------------------------------------------------------------

    @Test
    void claimingANewSessionStoresTheOwnerAndTheTime() {
        UUID session = UUID.randomUUID();
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        service.claim(session, alice, createdAt);

        assertThat(data.sessionOwner(session)).contains(alice);
        assertThat(sessions.findById(session)).hasValueSatisfying(
                found -> assertThat(found.getCreatedAt()).isEqualTo(createdAt));
    }

    @Test
    void claimingMyOwnSessionAgainIsHarmlessAndKeepsTheFirstTime() {
        UUID session = UUID.randomUUID();
        Instant first = T0;

        service.claim(session, alice, first);
        service.claim(session, alice, first.plusSeconds(600));

        assertThat(data.sessionRows(session)).isEqualTo(1);
        assertThat(sessions.findById(session)).hasValueSatisfying(
                found -> assertThat(found.getCreatedAt()).isEqualTo(first));
    }

    @Test
    void claimingSomeoneElsesSessionIsNotFoundAndChangesNothing() {
        UUID session = UUID.randomUUID();
        service.claim(session, alice, T0);

        assertThatThrownBy(() -> service.claim(session, bob, T0.plusSeconds(5)))
                .isInstanceOf(ChatSessionNotFoundException.class);

        assertThat(data.sessionOwner(session)).contains(alice);
        assertThat(data.sessionRows(session)).isEqualTo(1);
    }

    @RepeatedTest(15)
    void twoStudentsRacingForTheSameNewSessionIdLeaveExactlyOneOwnerAndOneRow() throws Exception {
        UUID session = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (UUID student : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        service.claim(session, student, Instant.now());
                        return true;
                    } catch (ChatSessionNotFoundException lost) {
                        return false;
                    }
                }));
            }
            ready.await();
            go.countDown();
            boolean firstWon = futures.get(0).get();
            boolean secondWon = futures.get(1).get();

            assertThat(firstWon ^ secondWon).as("exactly one claim wins").isTrue();
            assertThat(data.sessionRows(session)).isEqualTo(1);
            assertThat(data.sessionOwner(session)).contains(firstWon ? first : second);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- hasAnswers --------------------------------------------------------------------------------------

    @Test
    void aSessionWithoutMessagesHasNoAnswers() {
        UUID session = UUID.randomUUID();

        assertThat(service.hasAnswers(session, alice)).isFalse();
    }

    @Test
    void refusedAndFailedMessagesDoNotMakeASessionNonEmpty() {
        UUID session = UUID.randomUUID();
        message(session, alice, "off topic", "REFUSED", 1);
        message(session, alice, "provider error", "FAILED", 2);

        assertThat(service.hasAnswers(session, alice)).isFalse();
    }

    @Test
    void oneOkMessageMakesTheSessionNonEmpty() {
        UUID session = UUID.randomUUID();
        message(session, alice, "refused first", "REFUSED", 1);
        message(session, alice, "good one", "OK", 2);

        assertThat(service.hasAnswers(session, alice)).isTrue();
    }

    @Test
    void anOkMessageInAnotherSessionOrOfAnotherStudentDoesNotCount() {
        UUID session = UUID.randomUUID();
        message(UUID.randomUUID(), alice, "elsewhere", "OK", 1);
        message(session, bob, "someone else", "OK", 2);

        assertThat(service.hasAnswers(session, alice)).isFalse();
    }

    // ---- history -----------------------------------------------------------------------------------------

    @Test
    void historyIsTheLastSixOkMessagesOldestFirst() {
        UUID session = UUID.randomUUID();
        for (int i = 1; i <= 10; i++) {
            message(session, alice, "q" + i, "OK", i);
        }

        assertThat(questions(service.history(session, alice, 6)))
                .containsExactly("q5", "q6", "q7", "q8", "q9", "q10");
    }

    @Test
    void historySkipsRefusedAndFailedMessagesAndStillFillsTheWindowWithOkOnes() {
        UUID session = UUID.randomUUID();
        message(session, alice, "ok1", "OK", 1);
        message(session, alice, "ok2", "OK", 2);
        message(session, alice, "refused", "REFUSED", 3);
        message(session, alice, "ok3", "OK", 4);
        message(session, alice, "failed", "FAILED", 5);
        message(session, alice, "ok4", "OK", 6);
        message(session, alice, "refused2", "REFUSED", 7);
        message(session, alice, "failed2", "FAILED", 8);

        assertThat(questions(service.history(session, alice, 3))).containsExactly("ok2", "ok3", "ok4");
        assertThat(questions(service.history(session, alice, 6))).containsExactly("ok1", "ok2", "ok3", "ok4");
    }

    @Test
    void historyCarriesTheQuestionAndTheAnswerOfEachTurn() {
        UUID session = UUID.randomUUID();
        message(session, alice, "what is a collocation", "OK", 1);

        List<ChatMessage> history = service.history(session, alice, 6);

        assertThat(history).singleElement().satisfies(turn -> {
            assertThat(turn.getQuestion()).isEqualTo("what is a collocation");
            assertThat(turn.getAnswer()).isEqualTo("answer to what is a collocation");
        });
    }

    @Test
    void historyOfOtherSessionsAndOtherStudentsIsNeverIncluded() {
        UUID session = UUID.randomUUID();
        message(session, alice, "mine", "OK", 1);
        message(session, bob, "bobs in the same id", "OK", 2);
        message(UUID.randomUUID(), alice, "my other session", "OK", 3);

        assertThat(questions(service.history(session, alice, 6))).containsExactly("mine");
    }

    @Test
    void historyOfAnEmptySessionIsEmpty() {
        assertThat(service.history(UUID.randomUUID(), alice, 6)).isEmpty();
    }

    @Test
    void historyHonoursASmallerWindow() {
        UUID session = UUID.randomUUID();
        for (int i = 1; i <= 4; i++) {
            message(session, alice, "q" + i, "OK", i);
        }

        assertThat(questions(service.history(session, alice, 1))).containsExactly("q4");
    }

    // ---- displayMessages ---------------------------------------------------------------------------------

    @Test
    void displayShowsOkAndRefusedButNeverFailed() {
        UUID session = UUID.randomUUID();
        message(session, alice, "ok1", "OK", 1);
        message(session, alice, "failed", "FAILED", 2);
        message(session, alice, "refused", "REFUSED", 3);
        message(session, alice, "ok2", "OK", 4);

        assertThat(questions(service.displayMessages(session, alice, 50)))
                .containsExactly("ok1", "refused", "ok2");
    }

    @Test
    void displayIsTheLastNMessagesOldestFirst() {
        UUID session = UUID.randomUUID();
        for (int i = 1; i <= 8; i++) {
            message(session, alice, "q" + i, i % 3 == 0 ? "REFUSED" : "OK", i);
        }

        assertThat(questions(service.displayMessages(session, alice, 4))).containsExactly("q5", "q6", "q7", "q8");
    }

    @Test
    void displayNeverShowsAnotherStudentsMessages() {
        UUID session = UUID.randomUUID();
        message(session, alice, "mine", "OK", 1);
        message(session, bob, "not mine", "OK", 2);

        assertThat(questions(service.displayMessages(session, alice, 50))).containsExactly("mine");
        assertThat(questions(service.displayMessages(session, bob, 50))).containsExactly("not mine");
    }

    // ---- latestSessionId ---------------------------------------------------------------------------------

    @Test
    void aStudentWithoutAnyMessageHasNoLatestSession() {
        assertThat(service.latestSessionId(alice)).isEmpty();
    }

    @Test
    void theLatestSessionIsTheOneWithTheNewestMessage() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        message(older, alice, "a", "OK", 1);
        message(newer, alice, "b", "OK", 2);

        assertThat(service.latestSessionId(alice)).contains(newer);

        message(older, alice, "c", "OK", 3);

        assertThat(service.latestSessionId(alice)).contains(older);
    }

    @Test
    void anyStatusCountsForTheNewestMessage() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        message(first, alice, "a", "OK", 1);
        message(second, alice, "b", "OK", 2);
        message(first, alice, "c", "FAILED", 3);

        assertThat(service.latestSessionId(alice)).contains(first);

        message(second, alice, "d", "REFUSED", 4);

        assertThat(service.latestSessionId(alice)).contains(second);
    }

    @Test
    void aSessionWithoutMessagesIsIgnoredEvenWhenItWasCreatedLast() {
        UUID withMessages = UUID.randomUUID();
        UUID empty = UUID.randomUUID();
        data.insertSession(withMessages, alice, T0);
        message(withMessages, alice, "a", "OK", 1);
        data.insertSession(empty, alice, T0.plus(1, ChronoUnit.HOURS));

        assertThat(service.latestSessionId(alice)).contains(withMessages);
    }

    @Test
    void onlyMyOwnMessagesDecideMyLatestSession() {
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        message(mine, alice, "a", "OK", 1);
        message(theirs, bob, "b", "OK", 2);

        Optional<UUID> latest = service.latestSessionId(alice);

        assertThat(latest).contains(mine);
        assertThat(service.latestSessionId(bob)).contains(theirs);
    }
}
