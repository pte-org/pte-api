package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.repository.QuestionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides a list of recent topics to avoid. Reads the last 20 AI-generated questions
 * of the same task type and extracts topics from their generation_meta. Topics are
 * wrapped with delimiters and sanitized before use in the prompt.
 */
@Component
public class AvoidTopicsProvider {

    private static final int RECENT_TOPICS_COUNT = 20;
    private final QuestionRepository questionRepository;

    public AvoidTopicsProvider(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    /**
     * Returns a formatted string of recent topics for the given task type.
     * Format: "<topic>theme1</topic>, <topic>theme2</topic>, ..."
     */
    public String getAvoidTopicsForTaskType(String taskTypeKey) {
        List<String> recentTopics = questionRepository.findRecentTopicsByTaskType(
                taskTypeKey, PageRequest.of(0, RECENT_TOPICS_COUNT));

        if (recentTopics.isEmpty()) {
            return "No prior topics recorded.";
        }

        return recentTopics.stream()
                .filter(topic -> topic != null && !topic.isBlank())
                .map(topic -> String.format("<topic>%s</topic>", TopicHintPolicy.normalize(topic)))
                .collect(Collectors.joining(", "));
    }
}
