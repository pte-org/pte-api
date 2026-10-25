package com.pte.session.internal.service;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionGradingStatus;
import com.pte.session.dto.response.SessionGradingView;
import com.pte.session.dto.response.SessionPublicationView;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.repository.ExamSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the grading and publication state that lives on the session itself. Callers lock the session
 * row first ({@code lockForScoreReviewMutation} / {@code lockClosedForGradingCohort}); these methods do not lock.
 */
@Service
public class SessionGradingService {

    private final ExamSessionRepository sessionRepository;

    public SessionGradingService(ExamSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public Optional<SessionGradingView> findGrading(UUID sessionPublicId, UUID tenantId) {
        return sessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)
                .filter(session -> session.getGradingStatus() != null).map(SessionGradingService::toView);
    }

    @Transactional(readOnly = true)
    public List<SessionGradingView> findFrozen() {
        return sessionRepository.findFrozenGrading().stream().map(SessionGradingService::toView).toList();
    }

    @Transactional(readOnly = true)
    public long countFrozen(UUID tenantId) {
        return sessionRepository.countByTenantAndGradingStatus(tenantId, SessionGradingStatus.FROZEN);
    }

    @Transactional
    public SessionGradingView freezeGrading(UUID sessionPublicId, UUID tenantId, String markingMode,
            String previewVersion, UUID frozenByPublicId, Instant frozenAt) {
        ExamSession session = require(sessionPublicId, tenantId);
        session.freezeGrading(markingMode, previewVersion, frozenByPublicId, frozenAt);
        return toView(sessionRepository.save(session));
    }

    /** @return true only when this call completed the grading */
    @Transactional
    public boolean completeGrading(UUID sessionPublicId, UUID tenantId, Instant completedAt) {
        ExamSession session = require(sessionPublicId, tenantId);
        boolean completed = session.completeGrading(completedAt);
        if (completed) {
            sessionRepository.save(session);
        }
        return completed;
    }

    @Transactional(readOnly = true)
    public Optional<SessionPublicationView> findPublication(UUID sessionPublicId, UUID tenantId) {
        return sessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)
                .filter(session -> session.getPublicationPublicId() != null).map(SessionGradingService::toPublication);
    }

    @Transactional(readOnly = true)
    public List<SessionPublicationView> findPublications(UUID tenantId, Collection<UUID> sessionPublicIds) {
        if (sessionPublicIds.isEmpty()) {
            return List.of();
        }
        return sessionRepository.findByTenantAndPublicIds(tenantId, sessionPublicIds).stream()
                .filter(session -> session.getPublicationPublicId() != null)
                .map(SessionGradingService::toPublication).toList();
    }

    @Transactional
    public SessionPublicationView lockPublication(UUID sessionPublicId, UUID tenantId, UUID publicationPublicId,
            Instant lockedAt) {
        ExamSession session = require(sessionPublicId, tenantId);
        session.lockPublication(publicationPublicId, lockedAt);
        return toPublication(sessionRepository.save(session));
    }

    private ExamSession require(UUID sessionPublicId, UUID tenantId) {
        return sessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
    }

    private static SessionGradingView toView(ExamSession session) {
        return new SessionGradingView(session.getPublicId(), session.getTenantId(), session.getGradingMarkingMode(),
                session.getGradingStatus() == null ? null : session.getGradingStatus().name(),
                session.getGradingPreviewVersion(), session.getGradingFrozenByPublicId(),
                session.getGradingFrozenAt(), session.getGradingCompletedAt());
    }

    private static SessionPublicationView toPublication(ExamSession session) {
        return new SessionPublicationView(session.getPublicId(), session.getPublicationPublicId(),
                session.getPublicationLockedAt());
    }
}
