package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.dto.response.PracticeReviewResponse;
import com.pte.practice.internal.dto.response.PracticeTaskContentResponse;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
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
    private final PracticeSessionItemRepository itemRepository;
    private final JsonMapper jsonMapper;
    private final PracticeSessionItemService itemService;
    private final Clock clock;
    /** Set by Spring; null in focused tests, where a mock test review simply has no estimate. */
    private PracticeMockTestService mockTestService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setMockTestService(PracticeMockTestService mockTestService) {
        this.mockTestService = mockTestService;
    }

    public PracticeReviewService(PracticeSessionRepository sessionRepository,
            PracticeSessionItemRepository itemRepository, JsonMapper jsonMapper,
            PracticeSessionItemService itemService, Clock clock) {
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
        List<com.pte.practice.internal.domain.PracticeSessionItem> sessionItems = itemRepository
                .findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(session.getId());
        return new PracticeReviewResponse(session.getPublicId(), sessionItems.stream()
                .map(item -> new PracticeReviewResponse.Item(item.getPublicId(), item.getOrderIndex(), item.getTaskCode(),
                        item.getDisplayName(), item.getSection(), item.getStatus().name(), item.getPromptText(),
                        item.getSavedPayload(), item.getRendererKey(), item.getObjectiveScore(),
                        item.getObjectiveEvaluatedAt(), item.getAiScore(), item.getAiScoreStatus(),
                        PracticeScoringStateService.readSubScores(jsonMapper, item.getAiSubScoresJson()),
                        item.getAiProvider(), item.getAiModel(), item.getAiScoredAt(),
                        readOptions(item.getOptionsJson())))
                .toList(), estimatedScoresOf(session, sessionItems));
    }

    /** Reference estimate for a finished mock test; null for other sessions or while the test is unfinished. */
    private com.pte.practice.internal.dto.response.PracticeEstimatedScoresResponse estimatedScoresOf(
            PracticeSession session, List<com.pte.practice.internal.domain.PracticeSessionItem> items) {
        if (mockTestService == null || session.effectiveKind() != com.pte.practice.internal.domain.enums.PracticeSessionKind.MOCK_TEST
                || session.getStatus() != com.pte.practice.internal.domain.enums.PracticeSessionStatus.COMPLETED
                || session.getMockTestId() == null) {
            return null;
        }
        var template = mockTestService.templateForSession(session.getMockTestId());
        return template == null ? null : mockTestService.estimateFor(items, template);
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
