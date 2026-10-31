package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatTurn;
import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.exception.ChatInvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The earlier turns come from the student's browser, so they are not trusted: only the two chat roles are taken,
 * the amount is capped, and what is left always alternates so the provider accepts it.
 */
class ChatSendValidatorHistoryTest {

    private static final String PRIVATE_TEXT = "PRIVATE-STUDENT-TEXT";

    private final ChatAiProperties properties = new ChatAiProperties();
    private final ChatSendValidator validator = new ChatSendValidator(properties);

    private static ChatHistoryEntry user(String text) {
        return new ChatHistoryEntry("user", text);
    }

    private static ChatHistoryEntry assistant(String text) {
        return new ChatHistoryEntry("assistant", text);
    }

    private List<ChatTurn> validate(List<ChatHistoryEntry> history) {
        return validator.validate(new ChatSendCommand("next question", null, "en", null, history)).history();
    }

    @Test
    void noHistoryMeansNoEarlierTurns() {
        assertThat(validate(null)).isEmpty();
        assertThat(validate(List.of())).isEmpty();
    }

    @Test
    void anAlternatingConversationIsPassedOnWithTheProvidersRoleNames() {
        List<ChatTurn> turns = validate(List.of(user("q1"), assistant("a1"), user("q2"), assistant("a2")));

        assertThat(turns).containsExactly(ChatTurn.user("q1"), ChatTurn.model("a1"), ChatTurn.user("q2"),
                ChatTurn.model("a2"));
    }

    @Test
    void roleNamesAreMatchedIgnoringCaseAndSpaces() {
        assertThat(validate(List.of(new ChatHistoryEntry(" USER ", "q"), new ChatHistoryEntry("Assistant", "a"))))
                .hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"system", "model", "tool", "developer", "", "  "})
    void anyOtherRoleIsRejectedSoNobodyCanPlayTheSystem(String role) {
        assertThatThrownBy(() -> validate(List.of(user("q"), new ChatHistoryEntry(role, PRIVATE_TEXT))))
                .isInstanceOfSatisfying(ChatInvalidRequestException.class, ex ->
                        assertThat(ex.getUserMessage()).contains(ChatConstants.DETAIL_HISTORY_INVALID)
                                .doesNotContain(PRIVATE_TEXT));
    }

    @Test
    void aMissingRoleOrEntryIsRejected() {
        assertThatThrownBy(() -> validate(List.of(new ChatHistoryEntry(null, "q"))))
                .isInstanceOf(ChatInvalidRequestException.class);
        List<ChatHistoryEntry> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(() -> validate(withNull)).isInstanceOf(ChatInvalidRequestException.class);
    }

    @Test
    void blankTurnsAreDropped() {
        assertThat(validate(List.of(user("q1"), assistant("  "), assistant("a1")))).containsExactly(
                ChatTurn.user("q1"), ChatTurn.model("a1"));
    }

    @Test
    void aSingleTurnOverTheCapIsRejectedWithoutEchoingIt() {
        String huge = PRIVATE_TEXT.repeat(ChatConstants.HISTORY_ITEM_MAX_CHARS);

        assertThatThrownBy(() -> validate(List.of(user(huge))))
                .isInstanceOfSatisfying(ChatInvalidRequestException.class, ex ->
                        assertThat(ex.getUserMessage()).contains(ChatConstants.DETAIL_HISTORY_INVALID)
                                .doesNotContain(PRIVATE_TEXT));
    }

    @Test
    void onlyTheNewestTurnsAreKept() {
        List<ChatHistoryEntry> longConversation = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            longConversation.add(user("q" + i));
            longConversation.add(assistant("a" + i));
        }

        List<ChatTurn> turns = validate(longConversation);

        assertThat(turns).hasSize(properties.getHistorySize() * 2);
        assertThat(turns.getLast()).isEqualTo(ChatTurn.model("a15"));
        assertThat(turns.getFirst()).isEqualTo(ChatTurn.user("q10"));
    }

    @Test
    void theWholeHistoryIsCappedByCharactersDroppingTheOldestFirst() {
        String big = "x".repeat(ChatConstants.HISTORY_ITEM_MAX_CHARS);
        List<ChatHistoryEntry> history = List.of(user(big), assistant(big), user(big), assistant(big),
                user("newest question"), assistant("newest answer"));

        List<ChatTurn> turns = validate(history);

        assertThat(turns.stream().mapToInt(t -> t.text().length()).sum())
                .isLessThanOrEqualTo(ChatConstants.HISTORY_TOTAL_MAX_CHARS);
        assertThat(turns.getLast()).isEqualTo(ChatTurn.model("newest answer"));
        assertThat(turns.getFirst().role()).isEqualTo("user");
    }

    @Test
    void theConversationAlwaysStartsWithTheStudentAndEndsWithTheAssistant() {
        // The new question is the next user turn, and the provider needs the roles to alternate.
        assertThat(validate(List.of(assistant("stray"), user("q1"), assistant("a1"), user("unanswered"))))
                .containsExactly(ChatTurn.user("q1"), ChatTurn.model("a1"));
    }

    @Test
    void twoTurnsInARowFromTheSameSideKeepOnlyTheLaterOne() {
        assertThat(validate(List.of(user("old"), user("q1"), assistant("a1"), assistant("a1 again"))))
                .containsExactly(ChatTurn.user("q1"), ChatTurn.model("a1 again"));
    }

    @Test
    void contactDetailsAndControlCharactersInEarlierTurnsAreCleanedLikeTheQuestion() {
        List<ChatTurn> turns = validate(List.of(user("mail me at student@example.com\u0000 please"),
                assistant("ok")));

        assertThat(turns.getFirst().text()).doesNotContain("student@example.com").doesNotContain("\u0000")
                .contains("[email]");
    }
}
