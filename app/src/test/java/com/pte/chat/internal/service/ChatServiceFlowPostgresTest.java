package com.pte.chat.internal.service;

import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.exception.ChatQuotaExceededException;
import com.pte.chat.internal.exception.ChatSessionNotFoundException;
import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.GeminiResponses;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The send flow end to end on the real schema: what is stored, what the quota says, who may use a session and
 * what the provider is given as history. Only the Gemini HTTP endpoint is faked.
 */
@ChatPostgresJpaTest
class ChatServiceFlowPostgresTest extends ChatFlowPostgresBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant DAY_RESET = Instant.parse("2026-10-28T17:00:00Z");

    // ---- an answer ----------------------------------------------------------------------------------------

    @Test
    void anOnTopicAnswerIsStoredAndOneMessageIsCharged() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic("Use **has/have** + V3."));

        ChatReply reply = h.send(alice, session, "  How do I use the present perfect?  ");

        assertThat(reply.markdown()).isEqualTo("Use **has/have** + V3.");
        assertThat(reply.onTopic()).isTrue();
        assertThat(reply.fromCache()).isFalse();
        assertThat(reply.sessionId()).isEqualTo(session);
        assertThat(reply.quota().used()).isEqualTo(1);
        assertThat(reply.quota().remaining()).isEqualTo(19);
        assertThat(reply.quota().limit()).isEqualTo(20);
        assertThat(reply.quota().resetAt()).isEqualTo(DAY_RESET);
        assertThat(h.used(alice)).isEqualTo(1);
        assertThat(h.server.requestCount()).isEqualTo(1);

        Map<String, Object> row = data.messages(session).getFirst();
        assertThat(row.get("status")).isEqualTo("OK");
        assertThat(row.get("question")).isEqualTo("How do I use the present perfect?");
        assertThat(row.get("answer")).isEqualTo("Use **has/have** + V3.");
        assertThat(row.get("student_id")).isEqualTo(alice);
        assertThat(row.get("session_id")).isEqualTo(session);
        assertThat(row.get("language")).isEqualTo("en");
        assertThat(row.get("tokens_in")).isEqualTo(11);
        assertThat(row.get("tokens_out")).isEqualTo(22);
        assertThat(row.get("finish_reason")).isEqualTo("STOP");
        assertThat(row.get("from_cache")).isEqualTo(false);
        assertThat(row.get("on_topic")).isEqualTo(true);
        assertThat(row.get("model")).isEqualTo(GeminiResponses.SERVED_MODEL);
        assertThat(row.get("prompt_version")).isEqualTo(ChatPrompts.VERSION);
        assertThat(data.sessionOwner(session)).contains(alice);
    }

    @Test
    void theTenantOfTheCallerIsStoredWithTheMessage() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic("ok"));
        var caller = ChatFlowHarness.student(alice);

        h.send(caller, session, "Question");

        assertThat(data.messages(session).getFirst().get("tenant_id")).isEqualTo(caller.tenantId());
    }

    @Test
    void anOffTopicQuestionIsRefundedAndStoredAsRefused() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.offTopic());

        ChatReply reply = h.send(alice, session, "What is the capital of France?");

        assertThat(reply.onTopic()).isFalse();
        assertThat(reply.markdown()).isEqualTo(ChatPrompts.refusal("en"));
        assertThat(reply.quota().used()).isZero();
        assertThat(reply.quota().remaining()).isEqualTo(20);
        assertThat(h.used(alice)).isZero();
        assertThat(h.server.requestCount()).isEqualTo(1);
        Map<String, Object> row = data.messages(session).getFirst();
        assertThat(row.get("status")).isEqualTo("REFUSED");
        assertThat(row.get("on_topic")).isEqualTo(false);
        assertThat(row.get("answer")).isEqualTo(ChatPrompts.refusal("en"));
        assertThat(data.cacheRows()).isZero();
    }

    @Test
    void aRefusedQuestionLeavesTheSessionWithoutAnAnswer() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.offTopic());
        h.send(alice, session, "What is the capital of France?");

        assertThat(h.sessions.hasAnswers(session, alice)).isFalse();
    }

    // ---- history sent to the provider ---------------------------------------------------------------------

    @Test
    void theProviderGetsOnlyTheLastSixOkTurnsOfThisSessionOldestFirst() throws Exception {
        ChatFlowHarness h = start();
        UUID otherSession = UUID.randomUUID();
        h.server.enqueue(200, GeminiResponses.onTopic("answer to other"));
        h.send(alice, otherSession, "question in another session");
        List<String> plan = List.of("ok0", "ok1", "ok2", "REFUSED", "FAILED", "ok3", "ok4", "ok5", "ok6");
        for (String step : plan) {
            switch (step) {
                case "REFUSED" -> h.server.enqueue(200, GeminiResponses.offTopic());
                case "FAILED" -> h.server.enqueue(500, "");
                default -> h.server.enqueue(200, GeminiResponses.onTopic("answer to " + step));
            }
            String question = step.startsWith("ok") ? step : "was-" + step;
            if ("FAILED".equals(step)) {
                assertThatThrownBy(() -> h.send(alice, session, question))
                        .isInstanceOf(com.pte.chat.internal.exception.ChatBusyException.class);
            } else {
                h.send(alice, session, question);
            }
        }
        h.server.enqueue(200, GeminiResponses.onTopic("final answer"));

        h.send(alice, session, "final question");

        JsonNode contents = JSON.readTree(h.server.requests().getLast().body()).path("contents");
        List<String> roles = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (JsonNode turn : contents) {
            roles.add(turn.path("role").asString());
            texts.add(turn.path("parts").path(0).path("text").asString());
        }
        assertThat(roles).containsExactly("user", "model", "user", "model", "user", "model", "user", "model",
                "user", "model", "user", "model", "user");
        assertThat(texts).containsExactly(
                "ok1", "answer to ok1", "ok2", "answer to ok2", "ok3", "answer to ok3",
                "ok4", "answer to ok4", "ok5", "answer to ok5", "ok6", "answer to ok6", "final question");
        String body = h.server.requests().getLast().body();
        assertThat(body).doesNotContain("was-REFUSED").doesNotContain("was-FAILED")
                .doesNotContain(ChatPrompts.refusal("en")).doesNotContain("question in another session");
    }

    @Test
    void theFirstQuestionOfASessionIsSentWithoutAnyHistory() throws Exception {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic("fine"));

        h.send(alice, session, "first question");

        JsonNode contents = JSON.readTree(h.server.requests().getFirst().body()).path("contents");
        assertThat(contents.size()).isEqualTo(1);
        assertThat(contents.get(0).path("role").asString()).isEqualTo("user");
    }

    // ---- who may use a session ----------------------------------------------------------------------------

    @Test
    void anotherStudentsSessionIsNotFoundAndLeavesNothingBehind() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic("alice's answer"));
        h.send(alice, session, "alice question");
        int messagesBefore = data.messageRows(session);

        ChatSessionNotFoundException ex = catchThrowableOfType(ChatSessionNotFoundException.class,
                () -> h.send(bob, session, "bob tries alice's session"));

        assertThat(ex).isNotNull();
        assertThat(data.sessionOwner(session)).contains(alice);
        assertThat(data.sessionRows(session)).isEqualTo(1);
        assertThat(data.messageRows(session)).isEqualTo(messagesBefore);
        assertThat(data.messageRowsForStudent(bob)).isZero();
        assertThat(data.usageRows(bob)).isZero();
        assertThat(h.server.requestCount()).isEqualTo(1);
    }

    @Test
    void theHistoryOfAnotherStudentIsNeverSentEvenIfTheyShareASessionId() throws Exception {
        ChatFlowHarness h = start();
        // a foreign row with the same session id (e.g. written before ownership was enforced)
        data.insertMessage(session, bob, "bob secret question", "bob secret answer", "OK", Instant.now());
        h.server.enqueue(200, GeminiResponses.onTopic("ok"));
        data.insertSession(session, alice, Instant.now());

        h.send(alice, session, "alice question");

        assertThat(h.server.requests().getFirst().body()).doesNotContain("bob secret");
    }

    // ---- quota --------------------------------------------------------------------------------------------

    @Test
    void anExhaustedQuotaIs429WithTheResetTimeAndStoresNoSessionForANewId() throws IOException {
        ChatFlowHarness h = start(p -> p.setDailyLimit(2));
        h.server.enqueue(200, GeminiResponses.onTopic("one"));
        h.server.enqueue(200, GeminiResponses.onTopic("two"));
        h.send(alice, session, "first");
        h.send(alice, session, "second");
        UUID neverSeen = UUID.randomUUID();

        ChatQuotaExceededException ex = catchThrowableOfType(ChatQuotaExceededException.class,
                () -> h.send(alice, neverSeen, "third question in a brand new session"));

        assertThat(ex).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) ex.getData();
        assertThat(body.get("resetAt")).isEqualTo(DAY_RESET.toString());
        assertThat(body.get("limit")).isEqualTo(2);
        assertThat(body.get("remaining")).isEqualTo(0);
        assertThat(data.sessionRows(neverSeen)).isZero();
        assertThat(data.messageRows(neverSeen)).isZero();
        assertThat(h.used(alice)).isEqualTo(2);
        assertThat(h.server.requestCount()).isEqualTo(2);
    }

    @Test
    void withALimitOfOneTheSecondSequentialMessageIsRejected() throws IOException {
        ChatFlowHarness h = start(p -> p.setDailyLimit(1));
        h.server.enqueue(200, GeminiResponses.onTopic("one"));
        h.send(alice, session, "first");

        assertThatThrownBy(() -> h.send(alice, session, "second")).isInstanceOf(ChatQuotaExceededException.class);

        assertThat(h.used(alice)).isEqualTo(1);
        assertThat(h.server.requestCount()).isEqualTo(1);
    }

    @Test
    void aRefundedMessageGivesTheSlotBackSoTheStudentCanAskAgain() throws IOException {
        ChatFlowHarness h = start(p -> p.setDailyLimit(1));
        h.server.enqueue(200, GeminiResponses.offTopic());
        h.server.enqueue(200, GeminiResponses.onTopic("now on topic"));
        h.send(alice, session, "off topic");

        ChatReply reply = h.send(alice, session, "on topic");

        assertThat(reply.markdown()).isEqualTo("now on topic");
        assertThat(h.used(alice)).isEqualTo(1);
    }

    @Test
    void theQuotaEndpointReportsTodayAndAFreshStudentHasEverything() throws IOException {
        ChatFlowHarness h = start();
        assertThat(h.service.quota(ChatFlowHarness.student(alice)))
                .satisfies(fresh -> {
                    assertThat(fresh.used()).isZero();
                    assertThat(fresh.remaining()).isEqualTo(20);
                    assertThat(fresh.limit()).isEqualTo(20);
                    assertThat(fresh.resetAt()).isEqualTo(DAY_RESET);
                });
        h.server.enqueue(200, GeminiResponses.onTopic("one"));
        h.server.enqueue(200, GeminiResponses.onTopic("two"));
        h.send(alice, session, "first");
        h.send(alice, session, "second");

        assertThat(h.service.quota(ChatFlowHarness.student(alice))).satisfies(after -> {
            assertThat(after.used()).isEqualTo(2);
            assertThat(after.remaining()).isEqualTo(18);
        });
        assertThat(h.service.quota(ChatFlowHarness.student(bob)).used()).isZero();
    }

    // ---- latest session ----------------------------------------------------------------------------------

    @Test
    void aStudentWithoutAnySessionGetsNoLatestSession() throws IOException {
        ChatFlowHarness h = start();

        ChatLatestSession latest = h.service.latestSession(ChatFlowHarness.student(alice));

        assertThat(latest.sessionId()).isNull();
        assertThat(latest.messages()).isEmpty();
    }

    @Test
    void theLatestSessionIsTheOneWithTheNewestMessageAndShowsOkAndRefusedButNotFailed() throws Exception {
        ChatFlowHarness h = start();
        UUID older = UUID.randomUUID();
        h.server.enqueue(200, GeminiResponses.onTopic("older answer"));
        h.send(alice, older, "older question");
        h.server.enqueue(200, GeminiResponses.onTopic("newer answer"));
        h.send(alice, session, "newer question");
        h.server.enqueue(200, GeminiResponses.offTopic());
        h.send(alice, session, "newer off topic");
        h.server.enqueue(500, "");
        assertThatThrownBy(() -> h.send(alice, session, "newer failed"))
                .isInstanceOf(com.pte.chat.internal.exception.ChatBusyException.class);

        ChatLatestSession latest = h.service.latestSession(ChatFlowHarness.student(alice));

        assertThat(latest.sessionId()).isEqualTo(session);
        assertThat(latest.messages()).extracting(ChatMessage::getQuestion)
                .containsExactly("newer question", "newer off topic");
        assertThat(latest.messages()).extracting(ChatMessage::getStatus).containsExactly("OK", "REFUSED");
        assertThat(latest.messages().getFirst().getAnswer()).isEqualTo("newer answer");
    }

    @Test
    void aFailedMessageStillMakesItsSessionTheLatestOne() throws Exception {
        ChatFlowHarness h = start();
        UUID other = UUID.randomUUID();
        h.server.enqueue(200, GeminiResponses.onTopic("first answer"));
        h.send(alice, session, "first");
        h.server.enqueue(200, GeminiResponses.onTopic("other answer"));
        h.send(alice, other, "other");
        h.server.enqueue(500, "");
        assertThatThrownBy(() -> h.send(alice, session, "failing"))
                .isInstanceOf(com.pte.chat.internal.exception.ChatBusyException.class);

        assertThat(h.service.latestSession(ChatFlowHarness.student(alice)).sessionId()).isEqualTo(session);
    }

    @Test
    void anotherStudentsSessionIsNeverMyLatestSession() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic("bob answer"));
        h.send(bob, session, "bob question");

        assertThat(h.service.latestSession(ChatFlowHarness.student(alice)).sessionId()).isNull();
        assertThat(h.service.latestSession(ChatFlowHarness.student(bob)).sessionId()).isEqualTo(session);
    }
}
