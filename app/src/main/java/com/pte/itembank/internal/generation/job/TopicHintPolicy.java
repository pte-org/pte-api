package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;

import java.util.regex.Pattern;

/**
 * The optional topic is user text that ends up inside a prompt, so it is restricted to letters, digits, spaces and a
 * few punctuation marks. That removes control characters, markup, quotes, backticks and braces: nothing that could
 * close a delimiter or pass for an instruction structure. The prompt builder still wraps it as data.
 */
public final class TopicHintPolicy {

    private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{N} ,.&()/\\-]+");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private TopicHintPolicy() {
    }

    /** @return the normalized topic, or null when none was given */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String topic = WHITESPACE_RUN.matcher(raw.trim()).replaceAll(" ");
        if (topic.length() > QuestionGenerationConstants.MAX_TOPIC_HINT_LENGTH || !ALLOWED.matcher(topic).matches()) {
            throw new GenerationRequestInvalidException(QuestionGenerationConstants.GENERATION_TOPIC_HINT_INVALID,
                    QuestionGenerationConstants.GENERATION_TOPIC_HINT_INVALID_MESSAGE);
        }
        return topic;
    }
}
