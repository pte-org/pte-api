package com.pte.scoring.internal.service.grading;

import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.session.SessionService;
import com.pte.session.dto.response.SessionGradingView;
import com.pte.session.dto.response.SessionPublicationView;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Scoring's view of the grading and publication state that lives on the session. The session owns the data; this
 * translates its plain values into scoring's own marking mode and status types, so no other scoring class parses them.
 */
@Component
public class SessionGradingGateway {

    /** How a closed session is graded and how far it got. */
    public record Grading(UUID sessionPublicId, UUID tenantId, GradingMarkingMode markingMode,
                          GradingCohortStatus status, String previewVersion, UUID frozenByPublicId,
                          Instant frozenAt, Instant completedAt) {
    }

    /** The report publication that locked the session's scores. */
    public record Publication(UUID publicationPublicId, Instant lockedAt) {
    }

    private final SessionService sessionService;

    public SessionGradingGateway(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public Optional<Grading> grading(UUID tenantId, UUID sessionPublicId) {
        return sessionService.findGrading(sessionPublicId, tenantId).map(SessionGradingGateway::toGrading);
    }

    /** Frozen, not yet completed sessions of every tenant. */
    public List<Grading> frozen() {
        return sessionService.findFrozenGrading().stream().map(SessionGradingGateway::toGrading).toList();
    }

    public long countFrozen(UUID tenantId) {
        return sessionService.countFrozenGrading(tenantId);
    }

    public Grading freeze(UUID tenantId, UUID sessionPublicId, GradingMarkingMode markingMode,
            String previewVersion, UUID frozenByPublicId, Instant frozenAt) {
        return toGrading(sessionService.freezeGrading(sessionPublicId, tenantId, markingMode.name(), previewVersion,
                frozenByPublicId, frozenAt));
    }

    /** @return true only when this call completed the grading */
    public boolean complete(UUID tenantId, UUID sessionPublicId, Instant completedAt) {
        return sessionService.completeGrading(sessionPublicId, tenantId, completedAt);
    }

    public Optional<Publication> publication(UUID tenantId, UUID sessionPublicId) {
        return sessionService.findPublication(sessionPublicId, tenantId).map(SessionGradingGateway::toPublication);
    }

    public boolean isPublished(UUID tenantId, UUID sessionPublicId) {
        return publication(tenantId, sessionPublicId).isPresent();
    }

    /** Publications of the given sessions, keyed by session; sessions without one are absent. */
    public Map<UUID, Publication> publications(UUID tenantId, Collection<UUID> sessionPublicIds) {
        return sessionService.findPublications(tenantId, sessionPublicIds).stream()
                .collect(Collectors.toMap(SessionPublicationView::sessionPublicId, SessionGradingGateway::toPublication));
    }

    public Publication lockPublication(UUID tenantId, UUID sessionPublicId, UUID publicationPublicId,
            Instant lockedAt) {
        return toPublication(sessionService.lockPublication(sessionPublicId, tenantId, publicationPublicId, lockedAt));
    }

    private static Grading toGrading(SessionGradingView view) {
        return new Grading(view.sessionPublicId(), view.tenantId(), GradingMarkingMode.valueOf(view.markingMode()),
                GradingCohortStatus.valueOf(view.status()), view.previewVersion(), view.frozenByPublicId(),
                view.frozenAt(), view.completedAt());
    }

    private static Publication toPublication(SessionPublicationView view) {
        return new Publication(view.publicationPublicId(), view.lockedAt());
    }
}
