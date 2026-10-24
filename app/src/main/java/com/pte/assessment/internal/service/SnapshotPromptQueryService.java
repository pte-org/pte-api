package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamVersionItem;
import com.pte.assessment.dto.response.ExaminerQuestionPromptView;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.repository.ExamVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads a tenant-pinned question prompt without exposing scoring keys to human markers. */
@Service
public class SnapshotPromptQueryService {

    private static final TypeReference<List<FrozenOption>> OPTIONS = new TypeReference<>() { };

    private final ExamVersionRepository examVersionRepository;
    private final JsonMapper jsonMapper;

    public SnapshotPromptQueryService(ExamVersionRepository examVersionRepository, JsonMapper jsonMapper) {
        this.examVersionRepository = examVersionRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public Map<UUID, ExaminerQuestionPromptView> findForExaminer(
            Collection<UUID> itemPublicIds, UUID tenantId) {
        if (itemPublicIds == null || itemPublicIds.isEmpty() || tenantId == null) {
            return Map.of();
        }
        List<UUID> requestedItemIds = itemPublicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (requestedItemIds.isEmpty()) {
            return Map.of();
        }
        return examVersionRepository.findItemsForTenant(requestedItemIds, tenantId).stream()
                .collect(Collectors.toUnmodifiableMap(ExamVersionItem::getPublicId, this::toView));
    }

    private ExaminerQuestionPromptView toView(ExamVersionItem item) {
        String taskType = item.getTaskTypeKey() != null ? item.getTaskTypeKey()
                : item.getTaskTypeCode() != null ? item.getTaskTypeCode()
                : item.getPteTaskType() == null ? null : item.getPteTaskType().name();
        return new ExaminerQuestionPromptView(item.getOrderIndex(), item.getSection().name(), taskType, item.getTitle(),
                item.getPromptText(), item.getAudioPromptRef(), item.getImagePromptRef(),
                item.getMinWordCount(), item.getMaxWordCount(), parseOptions(item.getOptionsJson()));
    }

    private List<ExaminerQuestionPromptView.Option> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(optionsJson, OPTIONS).stream()
                    .map(option -> new ExaminerQuestionPromptView.Option(
                            option.orderIndex(), option.text(), option.blankIndex()))
                    .toList();
        } catch (JacksonException ex) {
            throw new IllegalStateException(AssessmentConstants.PINNED_QUESTION_OPTIONS_DECODE_FAILED, ex);
        }
    }

    /** Includes key-bearing fields only while decoding; none are copied into the public projection. */
    private record FrozenOption(String text, boolean correct, int orderIndex,
            Integer blankIndex, Integer correctGapIndex) {
    }
}
