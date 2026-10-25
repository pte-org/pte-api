package com.pte.itembank.internal.service;

import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.repository.TaskTypeCountProjection;
import com.pte.itembank.TaskTypeCodeCompatibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles published question pool queries for exam generation: counts, random draws, stable lists,
 * and question freezing for snapshot publication. Extracted from QuestionQueryService.
 */
@Service
public class QuestionPoolService {

    private final QuestionRepository questionRepository;
    private final QuestionTypeService questionTypeService;

    public QuestionPoolService(QuestionRepository questionRepository, QuestionTypeService questionTypeService) {
        this.questionRepository = questionRepository;
        this.questionTypeService = questionTypeService;
    }

    @Transactional(readOnly = true)
    public Map<PteTaskType, Long> countPublishedByTaskTypes(Set<PteTaskType> taskTypes) {
        Set<String> names = taskTypes.stream().map(Enum::name).collect(Collectors.toSet());
        Map<PteTaskType, Long> counts = new EnumMap<>(PteTaskType.class);
        taskTypes.forEach(taskType -> counts.put(taskType, 0L));
        for (TaskTypeCountProjection row : questionRepository.countPublishedSharedGroupedByTaskType(names)) {
            counts.put(TaskTypeCodeCompatibility.parse(row.getTaskType()), row.getCount());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public List<UUID> randomPublishedQuestionIds(PteTaskType taskType, int n) {
        return questionRepository.randomPublishedSharedIdsByTaskType(taskType.name(), n);
    }

    @Transactional(readOnly = true)
    public List<UUID> publishedQuestionIds(PteTaskType taskType) {
        return questionRepository.publishedSharedIdsByTaskType(taskType.name());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countPublishedByTaskTypeKeys(Set<String> taskTypeKeys) {
        Set<String> normalized = taskTypeKeys.stream()
                .map(this::normalizeTaskTypeKey)
                .collect(Collectors.toSet());
        Map<String, Long> counts = normalized.stream()
                .collect(Collectors.toMap(key -> key, key -> 0L, (left, right) -> left, java.util.LinkedHashMap::new));
        for (TaskTypeCountProjection row : questionRepository.countPublishedSharedGroupedByTaskTypeKey(normalized)) {
            counts.put(row.getTaskType(), row.getCount());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public List<UUID> randomPublishedQuestionIdsByTaskTypeKey(String taskTypeKey, int n) {
        return questionRepository.randomPublishedSharedIdsByTaskTypeKey(normalizeTaskTypeKey(taskTypeKey), n);
    }

    @Transactional(readOnly = true)
    public List<UUID> publishedQuestionIdsByTaskTypeKey(String taskTypeKey) {
        return questionRepository.publishedSharedIdsByTaskTypeKey(normalizeTaskTypeKey(taskTypeKey));
    }

    @Transactional(readOnly = true)
    public QuestionFreezeView freeze(UUID questionPublicId) {
        Question question = questionRepository.findWithOptionsByPublicId(questionPublicId)
                .orElseThrow(QuestionNotFoundException::new);
        if (question.getVisibility() != Visibility.SHARED || question.getStatus() != QuestionStatus.APPROVED
                || !question.isCurrent()
                || (!question.isAvailableForExam()
                        && (question.isAvailabilityFlagsExplicit() || question.getPool() != QuestionPool.EXAM))) {
            throw new QuestionNotFoundException();
        }
        return toFreezeView(question);
    }

    List<QuestionOption> deliveryOrder(Question question) {
        List<QuestionOption> natural = question.getOptions();
        boolean usesOptionOrderAsCorrectPosition = questionTypeService == null
                ? question.getPteTaskType() == PteTaskType.RE_ORDER_PARAGRAPHS
                : questionTypeService.findDefinitionByCode(question.getTaskTypeKey() == null
                        && question.getPteTaskType() != null ? question.getPteTaskType().name()
                                : question.getTaskTypeKey())
                        .map(definition -> definition.isUsesOptionOrderAsCorrectPosition())
                        .orElse(false);
        if (!usesOptionOrderAsCorrectPosition || natural.size() < 2) {
            return natural;
        }
        List<QuestionOption> rotated = new ArrayList<>(natural);
        Collections.rotate(rotated, 1);
        return rotated;
    }

    private QuestionFreezeView toFreezeView(Question question) {
        List<QuestionFreezeView.Option> options = deliveryOrder(question).stream()
                .map(o -> new QuestionFreezeView.Option(
                        o.getText(), o.isCorrect(), o.getOrderIndex(), o.getBlankIndex(), o.getCorrectGapIndex()))
                .toList();
        String taskTypeKey = question.getTaskTypeKey() == null && question.getPteTaskType() != null
                ? question.getPteTaskType().name() : question.getTaskTypeKey();
        String displayName = questionTypeService == null ? taskTypeKey
                : questionTypeService.findDefinitionByCode(taskTypeKey)
                        .map(definition -> definition.getDisplayName())
                        .orElse(taskTypeKey);
        return new QuestionFreezeView(
                question.getPublicId(), question.getPteTaskType(), question.getTitle(), question.getPromptText(),
                question.getAudioPromptRef(), question.getImagePromptRef(), question.getReferenceAnswerText(),
                question.getCorrectAnswerText(), question.getMinWordCount(), question.getMaxWordCount(), options,
                taskTypeKey,
                question.getTaskTypeSection() == null && question.getPteTaskType() != null
                        ? question.getPteTaskType().getSection().name() : question.getTaskTypeSection(), null,
                displayName);
    }

    private String normalizeTaskTypeKey(String raw) {
        try {
            return TaskTypeCodeCompatibility.normalizeTaskTypeKey(raw);
        } catch (RuntimeException ex) {
            throw new com.pte.itembank.internal.exception.QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
        }
    }
}
