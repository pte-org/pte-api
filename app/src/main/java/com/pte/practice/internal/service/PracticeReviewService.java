package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.dto.response.PracticeReviewResponse;
import com.pte.practice.internal.dto.response.PracticeTaskContentResponse;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Review projection of a session's items and their scores; it never starts scoring itself. */
@Service
public class PracticeReviewService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeResponseRepository itemRepository;
    private final JsonMapper jsonMapper;
    private final PracticeResponseService itemService;
    private final Clock clock;

    public PracticeReviewService(PracticeSessionRepository sessionRepository,
            PracticeResponseRepository itemRepository, JsonMapper jsonMapper,
            PracticeResponseService itemService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
        this.jsonMapper = jsonMapper;
        this.itemService = itemService;
        this.clock = clock;
    }

    /** Not read-only: it first repairs AI jobs that were lost or stuck, so the review never shows SCORING forever. */
    @Transactional
    public PracticeReviewResponse get(UUID sessionPublicId, CurrentUser caller) {
        PracticeSession session = sessionRepository
                .findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionPublicId, caller.userId())
                .orElseThrow(com.pte.practice.internal.exception.PracticeSessionNotFoundException::new);
        itemService.recoverScoring(session, clock.instant());
        return new PracticeReviewResponse(session.getPublicId(), itemRepository
                .findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(session.getId()).stream()
                .map(item -> new PracticeReviewResponse.Item(item.getPublicId(), item.getOrderIndex(), item.getTaskCode(),
                        item.getDisplayName(), item.getSection(), item.getStatus().name(), item.getPromptText(),
                        item.getSavedPayload(), item.getRendererKey(), item.getObjectiveScore(),
                        item.getObjectiveEvaluatedAt(), item.getAiScore(), item.getAiScoreStatus(),
                        PracticeScoringStateService.readSubScores(jsonMapper, item.getAiSubScoresJson()),
                        item.getAiProvider(), item.getAiModel(), item.getAiScoredAt(),
                        readOptions(item.getOptionsJson())))
                .toList());
    }

    /** The pinned option texts let the review show what was picked; a damaged snapshot only hides them. */
    private List<PracticeTaskContentResponse.Option> readOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(optionsJson, new TypeReference<List<PracticeTaskContentResponse.Option>>() { });
        } catch (JacksonException ex) {
            return List.of();
        }
    }
}
