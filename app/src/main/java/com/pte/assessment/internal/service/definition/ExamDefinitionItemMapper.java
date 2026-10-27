package com.pte.assessment.internal.service.definition;

import com.pte.assessment.internal.service.TaskTypeScoringMethods;
import com.pte.assessment.domain.ExamDefinitionItem;
import com.pte.assessment.dto.request.ExamDefinitionItemRequest;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskTypeCodeCompatibility;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts between request DTOs, persisted exam definition items, and their copies,
 * handling task type normalization and runtime profile resolution.
 */
class ExamDefinitionItemMapper {

    private final ExamDefinitionValidator validator;

    ExamDefinitionItemMapper(ExamDefinitionValidator validator) {
        this.validator = validator;
    }

    /**
     * Creates a deep copy of an exam definition item, pinning runtime profile
     * and preserving all scoring configuration.
     */
    ExamDefinitionItem copyItem(ExamDefinitionItem source) {
        ExamDefinitionItem copy = new ExamDefinitionItem();
        copy.setTaskType(source.getTaskType() == null ? null
                : TaskTypeCodeCompatibility.normalizeForLookup(source.getTaskType()));
        copy.setTaskTypeKey(logicalTaskKey(source));
        copy.setSection(source.getSection());
        copy.setSequence(source.getSequence());
        copy.setMinCount(source.getMinCount());
        copy.setMaxCount(source.getMaxCount());
        copy.setPrepSeconds(source.getPrepSeconds());
        copy.setResponseSeconds(source.getResponseSeconds());
        copy.setOverallWeight(source.getOverallWeight());
        copy.setSpeakingWeight(source.getSpeakingWeight());
        copy.setWritingWeight(source.getWritingWeight());
        copy.setReadingWeight(source.getReadingWeight());
        copy.setListeningWeight(source.getListeningWeight());
        TaskRuntimeProfileDescriptor pinned = source.pinnedRuntimeProfile();
        pinned = pinned == null ? validator.resolveActiveProfile(copy.getTaskTypeKey()) : pinned;
        copy.pinRuntimeProfile(pinned);
        copy.setScoringMethod(TaskTypeScoringMethods.resolve(pinned));
        return copy;
    }

    /**
     * Converts a request DTO into an exam definition item, normalizing task type
     * and resolving runtime profile. Tolerates missing profiles for incomplete CUSTOM drafts.
     */
    ExamDefinitionItem toEntity(ExamDefinitionItemRequest request) {
        ExamDefinitionItem item = new ExamDefinitionItem();
        final String canonicalTaskType;
        try {
            String rawKey = request.taskTypeKey() == null || request.taskTypeKey().isBlank()
                    ? request.taskType() : request.taskTypeKey();
            canonicalTaskType = TaskTypeCodeCompatibility.normalizeTaskTypeKey(rawKey);
        } catch (RuntimeException ex) {
            throw new ExamDefinitionValidationException(
                    ExamDefinitionConstants.TEMPLATE_TASK_TYPE_INVALID + (request.taskTypeKey() == null
                            ? request.taskType() : request.taskTypeKey()));
        }
        item.setTaskType(TaskTypeCodeCompatibility.isStandard(canonicalTaskType) ? canonicalTaskType : null);
        item.setTaskTypeKey(canonicalTaskType);
        final TaskRuntimeProfileDescriptor runtimeProfile;
        try {
            runtimeProfile = validator.resolveActiveProfile(canonicalTaskType);
            item.pinRuntimeProfile(runtimeProfile);
        } catch (RuntimeException ex) {
            // Allow incomplete CUSTOM drafts; validation happens at submit/activation
            item.setScoringMethod(com.pte.assessment.domain.enums.ScoringMethod.UNSCORED);
            item.setSection(request.section());
            item.setSequence(request.sequence());
            item.setMinCount(request.minCount());
            item.setMaxCount(request.maxCount());
            item.setPrepSeconds(request.prepSeconds());
            item.setResponseSeconds(request.responseSeconds());
            item.setSpeakingWeight(request.speakingWeight());
            item.setWritingWeight(request.writingWeight());
            item.setReadingWeight(request.readingWeight());
            item.setListeningWeight(request.listeningWeight());
            item.setOverallWeight(computeOverallWeight(
                    request.speakingWeight(), request.writingWeight(), request.readingWeight(),
                    request.listeningWeight()));
            return item;
        }
        item.setSection(request.section());
        item.setSequence(request.sequence());
        item.setMinCount(request.minCount());
        item.setMaxCount(request.maxCount());
        item.setPrepSeconds(request.prepSeconds());
        item.setResponseSeconds(request.responseSeconds());
        item.setScoringMethod(TaskTypeScoringMethods.resolve(runtimeProfile));
        item.setSpeakingWeight(request.speakingWeight());
        item.setWritingWeight(request.writingWeight());
        item.setReadingWeight(request.readingWeight());
        item.setListeningWeight(request.listeningWeight());
        item.setOverallWeight(computeOverallWeight(
                request.speakingWeight(), request.writingWeight(), request.readingWeight(), request.listeningWeight()));
        return item;
    }

    private String logicalTaskKey(ExamDefinitionItem item) {
        return item.getTaskTypeKey() == null ? TaskTypeCodeCompatibility.normalizeForLookup(item.getTaskType())
                : TaskTypeCodeCompatibility.normalizeTaskTypeKey(item.getTaskTypeKey());
    }

    /**
     * Computes overall skill weight as the mean of 4 communicative skills.
     * Verified against APEUni V5; rounding per row allows sum ≠ 100.00 (harmless).
     */
    private static BigDecimal computeOverallWeight(
            BigDecimal speakingWeight, BigDecimal writingWeight, BigDecimal readingWeight, BigDecimal listeningWeight) {
        BigDecimal sum = speakingWeight.add(writingWeight).add(readingWeight).add(listeningWeight);
        return sum.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
    }
}
