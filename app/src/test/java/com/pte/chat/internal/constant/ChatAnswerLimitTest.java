package com.pte.chat.internal.constant;

import com.pte.chat.internal.client.ChatOutputParser;
import com.pte.chat.internal.domain.ChatAnswerCache;
import com.pte.chat.internal.domain.ChatMessage;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One number rules how long a stored answer may be: the parser cap, both answer columns and the V7 schema must
 * agree, otherwise a valid answer is refunded at the very end because the insert is too long.
 */
class ChatAnswerLimitTest {

    private static int columnLength(Class<?> entity, String field) throws NoSuchFieldException {
        Column column = entity.getDeclaredField(field).getAnnotation(Column.class);
        assertThat(column).as("@Column on %s.%s", entity.getSimpleName(), field).isNotNull();
        return column.length();
    }

    @Test
    void theAnswerLimitIsTwentyThousandCharacters() {
        assertThat(ChatConstants.MAX_ANSWER_CHARS).isEqualTo(20_000);
    }

    @Test
    void theParserCapIsTheAnswerLimit() {
        assertThat(ChatOutputParser.MAX_MARKDOWN_CHARS).isEqualTo(ChatConstants.MAX_ANSWER_CHARS);
    }

    @Test
    void bothAnswerColumnsAreAsLongAsTheLimit() throws Exception {
        assertThat(columnLength(ChatMessage.class, "answer")).isEqualTo(ChatConstants.MAX_ANSWER_CHARS);
        assertThat(columnLength(ChatAnswerCache.class, "answer")).isEqualTo(ChatConstants.MAX_ANSWER_CHARS);
    }

    @Test
    void theQuestionColumnIsAsLongAsTheQuestionLimit() throws Exception {
        assertThat(columnLength(ChatMessage.class, "question")).isEqualTo(ChatConstants.MAX_QUESTION_CHARS);
    }
}
