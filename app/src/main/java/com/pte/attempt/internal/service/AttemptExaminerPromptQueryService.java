package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedItem;
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
    private final JsonMapper jsonMapper;

    public AttemptExaminerPromptQueryService(ExamAttemptRepository attemptRepository, JsonMapper jsonMapper) {
        this.attemptRepository = attemptRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public Map<UUID, AttemptExaminerPromptView> findForExaminer(UUID attemptPublicId, UUID sessionPublicId,
            UUID tenantId, Collection<UUID> pinnedItemPublicIds) {
        if (attemptPublicId == null || sessionPublicId == null || tenantId == null
                || pinnedItemPublicIds == null || pinnedItemPublicIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> requestedItemIds = pinnedItemPublicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (requestedItemIds.isEmpty()) {
            return Map.of();
        }

        ExamAttempt attempt = attemptRepository.findWithPinnedByPublicId(attemptPublicId).orElse(null);
        if (!belongsToRequestedScope(attempt, sessionPublicId, tenantId)) {
            return Map.of();
        }

        return attempt.getPinnedSnapshot().getItems().stream()
                .filter(item -> requestedItemIds.contains(item.getPublicId()))
                .collect(Collectors.toUnmodifiableMap(PinnedItem::getPublicId, this::toView));
    }

    private boolean belongsToRequestedScope(ExamAttempt attempt, UUID sessionPublicId, UUID tenantId) {
        return attempt != null && tenantId.equals(attempt.getTenantId())
                && sessionPublicId.equals(attempt.getSessionPublicId())
                && attempt.getPinnedSnapshot() != null
                && tenantId.equals(attempt.getPinnedSnapshot().getTenantId())
                && sessionPublicId.equals(attempt.getPinnedSnapshot().getSourceSessionPublicId());
    }

    private AttemptExaminerPromptView toView(PinnedItem item) {
        String taskType = item.getTaskTypeKey() != null ? item.getTaskTypeKey()
                : item.getTaskTypeCode() != null ? item.getTaskTypeCode() : item.getTaskType();
        return new AttemptExaminerPromptView(item.getPublicId(), item.getOrderIndex(), item.getSection(), taskType,
                item.getTitle(), item.getPromptText(), item.getAudioPromptRef(), item.getImagePromptRef(),
                item.getMinWordCount(), item.getMaxWordCount(), parseOptions(item.getOptionsJson()));
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
            throw new IllegalStateException(AttemptConstants.PINNED_ITEM_OPTIONS_PARSE_FAILED, ex);
        }
    }

    /** Includes key-bearing fields only while decoding; none are copied into the public projection. */
    private record FrozenOption(String text, boolean correct, int orderIndex,
            Integer blankIndex, Integer correctGapIndex) {
    }
}
