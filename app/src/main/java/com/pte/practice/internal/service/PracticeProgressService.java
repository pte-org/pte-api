package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeProgressStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.PracticeProgressEntry;
import com.pte.practice.internal.dto.response.PracticeProgressResponse;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
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
    private final PracticeSessionItemRepository itemRepository;
    private final Clock clock;

    public PracticeProgressService(PracticeSessionRepository sessionRepository,
            PracticeSessionItemRepository itemRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
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
        Map<Long, List<PracticeSessionItem>> itemsBySessionId = itemsBySession(sessions);
        for (PracticeSession session : sessions) {
            if (session.getId() == null) {
                continue;
            }
            PracticeProgressEntry entry = toEntry(session, itemsBySessionId.getOrDefault(session.getId(), List.of()));
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

    private Map<Long, List<PracticeSessionItem>> itemsBySession(Collection<PracticeSession> sessions) {
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
                .collect(Collectors.groupingBy(PracticeSessionItem::getPracticeSessionId,
                        HashMap::new, Collectors.toList()));
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private PracticeProgressEntry toEntry(PracticeSession session, List<PracticeSessionItem> items) {
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
                count(items, PracticeSessionItemStatus.ANSWERED),
                (int) items.stream().filter(item -> item.getStatus() == PracticeSessionItemStatus.PENDING
                        && item.getSavedPayload() != null && !item.getSavedPayload().isBlank()).count(),
                count(items, PracticeSessionItemStatus.SKIPPED),
                items.size(),
                countConfidence(items, ResponseConfidence.LOW),
                countConfidence(items, ResponseConfidence.MEDIUM),
                countConfidence(items, ResponseConfidence.HIGH),
                null);
    }

    private boolean hasResponse(PracticeSessionItem item) {
        return item.getStatus() == PracticeSessionItemStatus.ANSWERED
                || (item.getSavedPayload() != null && !item.getSavedPayload().isBlank());
    }

    private int count(List<PracticeSessionItem> items, PracticeSessionItemStatus status) {
        return (int) items.stream().filter(item -> item.getStatus() == status).count();
    }

    private int countConfidence(List<PracticeSessionItem> items, ResponseConfidence confidence) {
        return (int) items.stream().filter(item -> item.getConfidence() == confidence).count();
    }
}
