package com.pte.attempt.internal.service.query;

import com.pte.attempt.internal.service.ExamContentService;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.dto.response.AttemptExaminerPromptView;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
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
import java.util.stream.Collectors;

/**
 * Reads Examiner-safe prompts from the attempt's frozen content. The caller
 * supplies the assignment-authorized attempt/session/tenant tuple; this query
 * service still verifies the tuple before returning any prompt.
 */
@Service
public class AttemptExaminerPromptQueryService {

    private static final TypeReference<List<FrozenOption>> OPTIONS = new TypeReference<>() {
    };

    private final ExamAttemptRepository attemptRepository;
    private final ExamContentService contentService;
    private final JsonMapper jsonMapper;

    public AttemptExaminerPromptQueryService(ExamAttemptRepository attemptRepository,
            ExamContentService contentService, JsonMapper jsonMapper) {
        this.attemptRepository = attemptRepository;
        this.contentService = contentService;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public Map<UUID, AttemptExaminerPromptView> findForExaminer(UUID attemptPublicId, UUID sessionPublicId,
            UUID tenantId, Collection<UUID> itemPublicIds) {
        if (attemptPublicId == null || sessionPublicId == null || tenantId == null
                || itemPublicIds == null || itemPublicIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> requestedItemIds = itemPublicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (requestedItemIds.isEmpty()) {
            return Map.of();
        }

        ExamAttempt attempt = attemptRepository.findByPublicId(attemptPublicId).orElse(null);
        if (!belongsToRequestedScope(attempt, sessionPublicId, tenantId)) {
            return Map.of();
        }

        return contentService.contentItems(attempt.getExamVersionPublicId()).values().stream()
                .filter(item -> requestedItemIds.contains(item.itemPublicId()))
                .collect(Collectors.toUnmodifiableMap(SnapshotContentResponse.Item::itemPublicId, this::toView));
    }

    private boolean belongsToRequestedScope(ExamAttempt attempt, UUID sessionPublicId, UUID tenantId) {
        return attempt != null && tenantId.equals(attempt.getTenantId())
                && sessionPublicId.equals(attempt.getSessionPublicId());
    }

    private AttemptExaminerPromptView toView(SnapshotContentResponse.Item item) {
        String taskType = item.taskTypeKey() != null ? item.taskTypeKey()
                : item.taskTypeCode() != null ? item.taskTypeCode() : item.taskType();
        return new AttemptExaminerPromptView(item.itemPublicId(), item.orderIndex(), item.section(), taskType,
                item.title(), item.promptText(), item.audioPromptRef(), item.imagePromptRef(),
                item.minWordCount(), item.maxWordCount(), parseOptions(item.optionsJson()));
    }

    private List<AttemptExaminerPromptView.Option> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(optionsJson, OPTIONS).stream()
                    .map(option -> new AttemptExaminerPromptView.Option(
                            option.orderIndex(), option.text(), option.blankIndex()))
                    .toList();
        } catch (JacksonException ex) {
            throw new IllegalStateException(AttemptConstants.EXAM_ITEM_OPTIONS_PARSE_FAILED, ex);
        }
    }

    /** Includes key-bearing fields only while decoding; none are copied into the public projection. */
    private record FrozenOption(String text, boolean correct, int orderIndex,
            Integer blankIndex, Integer correctGapIndex) {
    }
}
