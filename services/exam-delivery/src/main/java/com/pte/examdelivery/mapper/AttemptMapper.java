package com.pte.examdelivery.mapper;

import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.TimerState;
import com.pte.examdelivery.dto.response.AttemptTaskResponse;
import com.pte.examdelivery.dto.response.BlankGroupView;
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
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

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
        List<FrozenOption> parsedOptions = parseFrozenOptions(item.optionsJson());
        requireHomogeneousBlankIndex(item.publicId(), parsedOptions);
        TaskView task = new TaskView(
                item.publicId(), item.orderIndex(), totalTasks, item.section(), item.taskType(), item.title(),
                item.promptText(), item.audioPromptRef(), item.imagePromptRef(), item.minWordCount(),
                item.maxWordCount(), toFlatOptions(parsedOptions), toBlankGroups(parsedOptions), item.prepSeconds(),
                item.responseSeconds(), timer.getPrepDeadline(), timer.getResponseDeadline(), Instant.now());
        return new AttemptTaskResponse(attempt.getPublicId(), attempt.getStatus().name(), false, task);
    }

    public AttemptTaskResponse toCompletedResponse(ExamAttempt attempt) {
        return new AttemptTaskResponse(attempt.getPublicId(), attempt.getStatus().name(), true, null);
    }

    public TimerStateResponse toTimerResponse(TimerState timer) {
        return new TimerStateResponse(timer.getCurrentOrderIndex(), timer.getPhase().name(),
                timer.getPrepDeadline(), timer.getResponseDeadline(), Instant.now());
    }

    private List<FrozenOption> parseFrozenOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(optionsJson, new TypeReference<List<FrozenOption>>() {
            });
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to parse pinned item optionsJson", ex);
        }
    }

    /**
     * A task's options must be either entirely blank-grouped or entirely flat
     * — never a mix — or {@link #toBlankGroups} would silently drop the
     * ungrouped entries (a bare null {@code blankIndex} classifier key isn't a
     * valid grouping bucket). This is an authoring data-integrity error, not a
     * runtime condition the mapper can recover from, so it fails loudly with a
     * clear message rather than falling through to a confusing
     * {@link NullPointerException} deep in a grouping pipeline.
     */
    private void requireHomogeneousBlankIndex(UUID pinnedItemPublicId, List<FrozenOption> parsed) {
        boolean anyGrouped = parsed.stream().anyMatch(o -> o.blankIndex() != null);
        boolean anyUngrouped = parsed.stream().anyMatch(o -> o.blankIndex() == null);
        if (anyGrouped && anyUngrouped) {
            throw new IllegalStateException(
                    "Pinned item " + pinnedItemPublicId + " has a mix of blank-grouped and ungrouped options — "
                            + "every option must either carry a blankIndex or none may.");
        }
    }

    /**
     * Flat, ungrouped option list for every task type except
     * {@code FILL_BLANKS_READING_WRITING} — {@code null} when the parsed
     * options are blank-grouped instead (never both populated on one task,
     * per {@link TaskView}'s doc comment).
     */
    private List<OptionView> toFlatOptions(List<FrozenOption> parsed) {
        if (parsed.isEmpty() || parsed.stream().anyMatch(o -> o.blankIndex() != null)) {
            return null;
        }
        return parsed.stream().map(o -> new OptionView(o.text(), String.valueOf(o.orderIndex()))).toList();
    }

    /**
     * Groups parsed options by {@code blankIndex} for {@code FILL_BLANKS_READING_WRITING}
     * — {@code null} when no option carries a {@code blankIndex} (every other task type).
     */
    private List<BlankGroupView> toBlankGroups(List<FrozenOption> parsed) {
        if (parsed.isEmpty() || parsed.stream().noneMatch(o -> o.blankIndex() != null)) {
            return null;
        }
        return parsed.stream()
                .collect(Collectors.groupingBy(FrozenOption::blankIndex, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new BlankGroupView(e.getKey(),
                        e.getValue().stream().map(o -> new OptionView(o.text(), String.valueOf(o.orderIndex()))).toList()))
                .toList();
    }

    /**
     * Mirrors authoring's frozen option shape (text/correct/orderIndex/blankIndex)
     * — {@code correct} is read but discarded. {@code blankIndex} is null except
     * for {@code FILL_BLANKS_READING_WRITING} options; must stay structurally
     * identical to authoring's {@code SnapshotPublishService.FrozenOption}, which
     * writes this same JSON shape.
     */
    private record FrozenOption(String text, boolean correct, int orderIndex, Integer blankIndex) {
    }
}
