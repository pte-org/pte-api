package com.pte.attempt.internal.mapper;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.dto.response.BlankGroupView;
import com.pte.attempt.internal.dto.response.OptionView;
import com.pte.attempt.internal.dto.response.TaskView;
import com.pte.attempt.internal.policy.ManualNavigationPolicy;
import com.pte.attempt.internal.service.cache.ExamItemView;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds student-facing responses, stripping every answer-bearing field
 * ({@code correctAnswerText}, {@code referenceAnswerText}, options'
 * {@code correct} flag) — those exist in {@link ExamItemView} for scoring
 * only and must never reach this mapper's output.
 */
@Component
public class AttemptMapper {

    private final JsonMapper jsonMapper;

    public AttemptMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public AttemptTaskResponse toTaskResponse(ExamAttempt attempt, ExamItemView item, int effectivePrepSeconds,
            int effectiveResponseSeconds, List<ExamItemView> allItems) {
        return toTaskResponse(attempt, item, effectivePrepSeconds, effectiveResponseSeconds, allItems, null);
    }

    /**
     * {@code encryptionPublicKey} is non-null only from the {@code startAttempt}/{@code createAndPin} call site.
     *
     * <p>{@code effectivePrepSeconds}/{@code effectiveResponseSeconds} — NOT
     * {@code item.prepSeconds()}/{@code item.responseSeconds()} directly: for
     * a section-scoped item (READING) these differ, reflecting the live
     * remaining shared-section budget rather than just this one item's own
     * slice. See {@code TimerService#resolveEffectivePrepSeconds}/{@code
     * resolveEffectiveResponseSeconds}, which every caller here must go
     * through instead of reading the item's raw values. This is the ONLY
     * timing info the client receives per task — no prepDeadline/
     * responseDeadline/serverNow, since the client computes its own local
     * countdown entirely from these two ints.
     */
    public AttemptTaskResponse toTaskResponse(ExamAttempt attempt, ExamItemView item, int effectivePrepSeconds,
            int effectiveResponseSeconds, List<ExamItemView> allItems, String encryptionPublicKey) {
        int totalTasks = allItems.size();
        List<FrozenOption> parsedOptions = parseFrozenOptions(item.optionsJson());
        requireHomogeneousBlankIndex(item.publicId(), parsedOptions);
        boolean navigationAllowed = ManualNavigationPolicy.allowsSection(item.section());
        boolean canNavigatePrevious = navigationAllowed && item.orderIndex() > 0
                && ManualNavigationPolicy.allowsSection(sectionAt(allItems, item.orderIndex() - 1));
        boolean hasNextItem = item.orderIndex() + 1 < totalTasks;
        boolean canNavigateNext = navigationAllowed
                && (!hasNextItem || ManualNavigationPolicy.allowsSection(sectionAt(allItems, item.orderIndex() + 1)));
        TaskView task = new TaskView(
                item.publicId(), item.orderIndex(), totalTasks, item.section(), item.taskType(), item.title(),
                item.promptText(), item.audioPromptRef(), item.imagePromptRef(), item.minWordCount(),
                item.maxWordCount(), toFlatOptions(parsedOptions), toBlankGroups(parsedOptions), effectivePrepSeconds,
                effectiveResponseSeconds, attempt.getExamEndTime(), item.preListenSeconds(), item.preRecordSeconds(),
                item.imageUrl(), item.audioUrl(), item.taskTypeCode() != null ? item.taskTypeCode() : item.taskType(),
                item.runtime(), item.taskTypeDisplayName(), canNavigatePrevious, canNavigateNext);
        // A null lockdownMode is the client's "no lockdown" contract, same as toCompletedResponse.
        return new AttemptTaskResponse(attempt.getPublicId(), attempt.getStatus().name(), false, task, encryptionPublicKey,
                attempt.getLockdownMode(), attempt.getAttemptNumber(),
                remainingRetries(attempt), remainingRetries(attempt) > 0,
                attempt.getExamMode());
    }

    private String sectionAt(List<ExamItemView> allItems, int orderIndex) {
        return allItems.stream()
                .filter(candidate -> candidate.orderIndex() == orderIndex)
                .map(ExamItemView::section)
                .findFirst()
                .orElse(null);
    }

    public AttemptTaskResponse toCompletedResponse(ExamAttempt attempt) {
        int remainingRetries = remainingRetries(attempt);
        return new AttemptTaskResponse(attempt.getPublicId(), attempt.getStatus().name(), true, null, null, null,
                attempt.getAttemptNumber(), remainingRetries, remainingRetries > 0, null);
    }

    private int remainingRetries(ExamAttempt attempt) {
        return Math.max(0, attempt.getMaxRetriesPerStudent() - attempt.getAttemptNumber() + 1);
    }

    private List<FrozenOption> parseFrozenOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(optionsJson, new TypeReference<List<FrozenOption>>() {
            });
        } catch (JacksonException ex) {
            throw new IllegalStateException(AttemptConstants.EXAM_ITEM_OPTIONS_PARSE_FAILED, ex);
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
    private void requireHomogeneousBlankIndex(UUID itemPublicId, List<FrozenOption> parsed) {
        boolean anyGrouped = parsed.stream().anyMatch(o -> o.blankIndex() != null);
        boolean anyUngrouped = parsed.stream().anyMatch(o -> o.blankIndex() == null);
        if (anyGrouped && anyUngrouped) {
            throw new IllegalStateException(
                    String.format(AttemptConstants.MIXED_BLANK_INDEX_OPTIONS, itemPublicId));
        }
    }

    /**
     * Flat, ungrouped option list for every task type except
     * {@code FILL_IN_THE_BLANKS_DROPDOWN} — {@code null} when the parsed
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
     * Groups parsed options by {@code blankIndex} for {@code FILL_IN_THE_BLANKS_DROPDOWN}
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
     * Mirrors assessment's frozen option shape (text/correct/orderIndex/blankIndex)
     * — {@code correct} is read but discarded. {@code blankIndex} is null except
     * for {@code FILL_IN_THE_BLANKS_DROPDOWN} options; must stay structurally
     * identical to assessment's own frozen-option shape, which writes this same
     * JSON into {@code SnapshotItem.optionsJson}.
     */
    private record FrozenOption(String text, boolean correct, int orderIndex, Integer blankIndex) {
    }
}
