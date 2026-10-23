package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.dto.response.PracticeReviewResponse;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/** Read-only review projection; it never evaluates or generates AI content. */
@Service
public class PracticeReviewService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeSessionItemRepository itemRepository;
    private final JsonMapper jsonMapper;

    @Autowired
    public PracticeReviewService(PracticeSessionRepository sessionRepository,
            PracticeSessionItemRepository itemRepository, JsonMapper jsonMapper) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
        this.jsonMapper = jsonMapper;
    }

    /** Compatibility constructor for focused unit tests. */
    public PracticeReviewService(PracticeSessionRepository sessionRepository,
            PracticeSessionItemRepository itemRepository) {
        this(sessionRepository, itemRepository, JsonMapper.builder().build());
    }

    @Transactional(readOnly = true)
    public PracticeReviewResponse get(UUID sessionPublicId, CurrentUser caller) {
        PracticeSession session = sessionRepository
                .findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionPublicId, caller.userId())
                .orElseThrow(com.pte.practice.internal.exception.PracticeSessionNotFoundException::new);
        return new PracticeReviewResponse(session.getPublicId(), itemRepository
                .findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(session.getId()).stream()
                .map(item -> new PracticeReviewResponse.Item(item.getPublicId(), item.getOrderIndex(), item.getTaskCode(),
                        item.getDisplayName(), item.getSection(), item.getStatus().name(), item.getPromptText(),
                        item.getSavedPayload(), item.getRendererKey(), item.getObjectiveScore(),
                        item.getObjectiveEvaluatedAt(), item.getAiScore(), item.getAiScoreStatus(),
                        readSubScores(item.getAiSubScoresJson()),
                        item.getAiProvider(), item.getAiModel(), item.getAiScoredAt()))
                .toList());
    }

    private java.util.Map<String, Integer> readSubScores(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Map.of();
        }
        try {
            JsonNode root = jsonMapper.readTree(value);
            if (root == null || !root.isObject()) return java.util.Map.of();
            java.util.Map<String, Integer> scores = new java.util.LinkedHashMap<>();
            root.properties().forEach(entry -> {
                if (entry.getValue() != null && entry.getValue().isNumber()) {
                    scores.put(entry.getKey(), entry.getValue().asInt());
                }
            });
            return java.util.Map.copyOf(scores);
        } catch (RuntimeException exception) {
            return java.util.Map.of();
        }
    }
}
