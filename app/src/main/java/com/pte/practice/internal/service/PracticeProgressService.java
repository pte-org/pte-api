package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.enums.PracticeProgressStatus;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
import com.pte.practice.internal.domain.enums.PracticeScoringStatus;
import com.pte.practice.internal.dto.response.PracticeProgressEntry;
import com.pte.practice.internal.dto.response.PracticeProgressResponse;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.scoring.PracticeScoringRoute;
import com.pte.scoring.ScoringService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the student's practice history without consulting current
 * entitlement. Revoking a plan blocks new mutations, but does not erase this
 * read-only history.
 */
@Service
public class PracticeProgressService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeResponseRepository itemRepository;
    private final ScoringService scoringService;
    private final Clock clock;

    public PracticeProgressService(PracticeSessionRepository sessionRepository,
            PracticeResponseRepository itemRepository, ScoringService scoringService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
        this.scoringService = scoringService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PracticeProgressResponse getProgress(UUID studentPublicId) {
        if (studentPublicId == null) {
            return new PracticeProgressResponse(List.of(), 0, 0, 0, 0, clock.instant(), false);
        }
        List<PracticeProgressEntry> entries = new ArrayList<>();
        List<PracticeSession> sessions = sessionRepository
                .findTop100ByStudentPublicIdAndDeletedFalseOrderByCreatedAtDesc(studentPublicId);
        long totalSessionCount = sessionRepository.countByStudentPublicIdAndDeletedFalse(studentPublicId);
        Map<Long, List<PracticeResponse>> itemsBySessionId = itemsBySession(sessions);
        Map<String, Boolean> aiScoredByTask = new HashMap<>();
        for (PracticeSession session : sessions) {
            if (session.getId() == null) {
                continue;
            }
            PracticeProgressEntry entry = toEntry(session, itemsBySessionId.getOrDefault(session.getId(), List.of()),
                    aiScoredByTask);
            if (entry != null) {
                entries.add(entry);
            }
        }
        int completedSessions = (int) entries.stream()
                .filter(entry -> entry.status() == PracticeProgressStatus.COMPLETED_PENDING_SCORE
                        || entry.status() == PracticeProgressStatus.COMPLETED_SCORED
                        || entry.status() == PracticeProgressStatus.SCORING_FAILED)
                .count();
        int answeredItems = entries.stream().mapToInt(PracticeProgressEntry::answeredItemCount).sum();
        int totalItems = entries.stream().mapToInt(PracticeProgressEntry::totalItemCount).sum();
        return new PracticeProgressResponse(entries, safeInt(totalSessionCount), completedSessions,
                answeredItems, totalItems, clock.instant(), totalSessionCount > sessions.size());
    }

    private Map<Long, List<PracticeResponse>> itemsBySession(Collection<PracticeSession> sessions) {
        List<Long> sessionIds = sessions.stream()
                .map(PracticeSession::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        return itemRepository
                .findByPracticeSessionIdInAndDeletedFalseOrderByPracticeSessionIdAscOrderIndexAsc(sessionIds)
                .stream()
                .filter(item -> item.getPracticeSessionId() != null)
                .collect(Collectors.groupingBy(PracticeResponse::getPracticeSessionId,
                        HashMap::new, Collectors.toList()));
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private PracticeProgressEntry toEntry(PracticeSession session, List<PracticeResponse> items,
            Map<String, Boolean> aiScoredByTask) {
        boolean hasResponse = items.stream().anyMatch(this::hasResponse);
        PracticeProgressStatus progressStatus = switch (session.getStatus()) {
            case COMPLETED -> hasResponse ? PracticeProgressStatus.COMPLETED_PENDING_SCORE : null;
            case IN_PROGRESS -> PracticeProgressStatus.IN_PROGRESS;
            case EXPIRED -> hasResponse ? PracticeProgressStatus.IN_PROGRESS : null;
            case OVERVIEW, DISCARDED -> null;
        };
        if (progressStatus == null) {
            return null;
        }
        return new PracticeProgressEntry(
                session.getPublicId(),
                session.getProductCode(),
                session.getTitle(),
                progressStatus,
                session.getStatus(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getLastActivityAt(),
                count(items, PracticeResponseStatus.ANSWERED),
                (int) items.stream().filter(item -> item.getStatus() == PracticeResponseStatus.PENDING
                        && item.getSavedPayload() != null && !item.getSavedPayload().isBlank()).count(),
                count(items, PracticeResponseStatus.SKIPPED),
                items.size(),
                countConfidence(items, ResponseConfidence.LOW),
                countConfidence(items, ResponseConfidence.MEDIUM),
                countConfidence(items, ResponseConfidence.HIGH),
                averageScore(items),
                session.effectiveKind().name(),
                session.getSkillCode(),
                singleTaskCode(session.getSelectedTaskTypes()),
                (int) items.stream().filter(item -> isUnscoredAi(item, aiScoredByTask)).count(),
                countScoring(items, PracticeScoringStatus.SCORING),
                countScoring(items, PracticeScoringStatus.FAILED));
    }

    /** An answered speaking/writing item still waiting for the student's "Chấm điểm" request. */
    private boolean isUnscoredAi(PracticeResponse item, Map<String, Boolean> aiScoredByTask) {
        if (item.getStatus() != PracticeResponseStatus.ANSWERED
                || item.getScoringStatus() != PracticeScoringStatus.NONE || item.getTaskCode() == null) {
            return false;
        }
        return aiScoredByTask.computeIfAbsent(item.getTaskCode(), code -> {
            PracticeScoringRoute route = scoringService.practiceScoringRoute(code);
            return route == PracticeScoringRoute.AI_SPEECH || route == PracticeScoringRoute.AI_TEXT;
        });
    }

    private int countScoring(List<PracticeResponse> items, PracticeScoringStatus status) {
        return (int) items.stream().filter(item -> item.getScoringStatus() == status).count();
    }

    private Double averageScore(List<PracticeResponse> items) {
        var scores = items.stream()
                .filter(item -> item.getStatus() == PracticeResponseStatus.ANSWERED)
                .map(item -> item.getAiScore() != null ? item.getAiScore() : item.getObjectiveScore())
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .summaryStatistics();
        return scores.getCount() == 0 ? null : Math.round(scores.getAverage() * 10) / 10.0;
    }

    private String singleTaskCode(String selectedTaskTypes) {
        if (selectedTaskTypes == null || selectedTaskTypes.isBlank()) {
            return null;
        }
        String[] codes = selectedTaskTypes.split(",");
        return codes.length == 1 ? codes[0].trim() : null;
    }

    private boolean hasResponse(PracticeResponse item) {
        return item.getStatus() == PracticeResponseStatus.ANSWERED
                || (item.getSavedPayload() != null && !item.getSavedPayload().isBlank());
    }

    private int count(List<PracticeResponse> items, PracticeResponseStatus status) {
        return (int) items.stream().filter(item -> item.getStatus() == status).count();
    }

    private int countConfidence(List<PracticeResponse> items, ResponseConfidence confidence) {
        return (int) items.stream().filter(item -> item.getConfidence() == confidence).count();
    }
}
