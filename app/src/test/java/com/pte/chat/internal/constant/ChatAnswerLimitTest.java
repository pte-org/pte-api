package com.pte.chat.internal.constant;

import com.pte.chat.internal.client.ChatOutputParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One number rules how long an answer may be: the parser cap and the longest earlier turn a request may carry
 * must agree, otherwise a valid answer could not be sent back as context.
 */
class ChatAnswerLimitTest {

    @Test
    void theAnswerLimitIsTwentyThousandCharacters() {
        assertThat(ChatConstants.MAX_ANSWER_CHARS).isEqualTo(20_000);
    }

    @Test
    void theParserCapIsTheAnswerLimit() {
        assertThat(ChatOutputParser.MAX_MARKDOWN_CHARS).isEqualTo(ChatConstants.MAX_ANSWER_CHARS);
    }

    @Test
    void anEarlierTurnMayBeAsLongAsAnAnswerSoARealAnswerCanAlwaysBeSentBackAsContext() {
        assertThat(ChatConstants.HISTORY_ITEM_MAX_CHARS).isEqualTo(ChatConstants.MAX_ANSWER_CHARS);
    }

    @Test
    void theWholeHistoryCapHoldsAtLeastOneMaximumTurn() {
        assertThat(ChatConstants.HISTORY_TOTAL_MAX_CHARS).isGreaterThanOrEqualTo(ChatConstants.HISTORY_ITEM_MAX_CHARS);
    }
}
