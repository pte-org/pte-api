package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamVersionItem;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.itembank.TaskRuntimeContractConstants;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.dto.response.QuestionFreezeView;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Creates and validates snapshot items by mapping frozen questions to runtime profiles
 * and serializing question content for immutable storage in exam versions.
 */
class SnapshotItemFactory {

    private final JsonMapper jsonMapper;
    private final SnapshotRuntimeProfileResolver profileResolver;

    SnapshotItemFactory(JsonMapper jsonMapper, SnapshotRuntimeProfileResolver profileResolver) {
        this.jsonMapper = jsonMapper;
        this.profileResolver = profileResolver;
    }

    /**
     * Converts a frozen question into a snapshot item with runtime metadata and options.
     * Runtime profile is resolved from the active template or legacy task types.
     */
    ExamVersionItem toSnapshotItem(QuestionFreezeView question, PteSection section, int orderIndex,
            Map<String, ExamDefinitionItemResponse> templateItemsByTaskType) {
        ExamVersionItem item = new ExamVersionItem();
        item.setSourceQuestionPublicId(question.sourceQuestionPublicId());
        item.setPteTaskType(question.pteTaskType());

        String taskTypeKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                question.taskTypeKey() == null && question.pteTaskType() != null
                        ? question.pteTaskType().name() : question.taskTypeKey());
        item.setTaskTypeKey(taskTypeKey);
        item.setTaskTypeDisplayName(question.taskTypeDisplayName() == null
                ? taskTypeKey : question.taskTypeDisplayName());

        TaskRuntimeProfileDescriptor runtime = profileResolver.resolveRuntimeProfile(taskTypeKey,
                question.pteTaskType(), templateItemsByTaskType);
        item.pinRuntimeProfile(runtime, TaskRuntimeContractConstants.MAPPING_VERSION_CANONICAL,
                TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_CANONICAL);
        item.setSection(section);
        item.setOrderIndex(orderIndex);
        item.setTitle(question.title());
        item.setPromptText(question.promptText());
        item.setAudioPromptRef(question.audioPromptRef());
        item.setImagePromptRef(question.imagePromptRef());
        item.setReferenceAnswerText(question.referenceAnswerText());
        item.setCorrectAnswerText(question.correctAnswerText());
        item.setMinWordCount(question.minWordCount());
        item.setMaxWordCount(question.maxWordCount());
        item.setOptionsJson(serializeOptions(question.options()));
        return item;
    }

    /**
     * Serializes question options into JSON for storage in the snapshot.
     * Handles FrozenOption format with support for dropdown blanks and drag-and-drop gaps.
     */
    private String serializeOptions(List<QuestionFreezeView.Option> options) {
        List<FrozenOption> frozen = options.stream()
                .map(o -> new FrozenOption(o.text(), o.correct(), o.orderIndex(), o.blankIndex(), o.correctGapIndex()))
                .toList();
        try {
            return jsonMapper.writeValueAsString(frozen);
        } catch (JacksonException ex) {
            throw new IllegalStateException(AssessmentConstants.SNAPSHOT_OPTIONS_SERIALIZATION_FAILED, ex);
        }
    }

    /**
     * Frozen option shape stored in {@code ExamVersionItem.optionsJson}.
     * {@code blankIndex} is null except for {@code FILL_IN_THE_BLANKS_DROPDOWN} options,
     * where it groups options under their owning blank; {@code correctGapIndex} is
     * scoring-only, set only for {@code FILL_IN_THE_BLANKS_DRAG_AND_DROP} correct options.
     */
    private record FrozenOption(String text, boolean correct, int orderIndex, Integer blankIndex, Integer correctGapIndex) {
    }
}
