package com.pte.examdelivery.mapper;

import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.TimerState;
import com.pte.examdelivery.dto.response.AttemptTaskResponse;
import com.pte.examdelivery.dto.response.OptionView;
import com.pte.examdelivery.dto.response.TaskView;
import com.pte.examdelivery.dto.response.TimerStateResponse;
import com.pte.examdelivery.service.cache.PinnedItemView;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

/**
 * Builds student-facing responses, stripping every answer-bearing field
 * ({@code correctAnswerText}, {@code referenceAnswerText}, options'
 * {@code correct} flag) — those exist in {@link PinnedItemView} for scoring
 * only and must never reach this mapper's output.
 */
@Component
public class AttemptMapper {

    private final JsonMapper jsonMapper;

    public AttemptMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public AttemptTaskResponse toTaskResponse(ExamAttempt attempt, PinnedItemView item, TimerState timer, int totalTasks) {
        TaskView task = new TaskView(
                item.publicId(), item.orderIndex(), totalTasks, item.section(), item.taskType(), item.title(),
                item.promptText(), item.audioPromptRef(), item.imagePromptRef(), item.minWordCount(),
                item.maxWordCount(), parseOptions(item.optionsJson()), item.prepSeconds(), item.responseSeconds(),
                timer.getPrepDeadline(), timer.getResponseDeadline(), Instant.now());
        return new AttemptTaskResponse(attempt.getPublicId(), attempt.getStatus().name(), false, task);
    }

    public AttemptTaskResponse toCompletedResponse(ExamAttempt attempt) {
        return new AttemptTaskResponse(attempt.getPublicId(), attempt.getStatus().name(), true, null);
    }

    public TimerStateResponse toTimerResponse(TimerState timer) {
        return new TimerStateResponse(timer.getCurrentOrderIndex(), timer.getPhase().name(),
                timer.getPrepDeadline(), timer.getResponseDeadline(), Instant.now());
    }

    private List<OptionView> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            List<FrozenOption> parsed = jsonMapper.readValue(optionsJson, new TypeReference<List<FrozenOption>>() {
            });
            return parsed.stream().map(o -> new OptionView(o.text(), o.orderIndex())).toList();
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to parse pinned item optionsJson", ex);
        }
    }

    /** Mirrors authoring's frozen option shape (text/correct/orderIndex) — correct is read but discarded. */
    private record FrozenOption(String text, boolean correct, int orderIndex) {
    }
}
