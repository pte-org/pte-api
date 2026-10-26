package com.pte.scoring.internal.service.examiner;

import com.pte.session.SessionService;
import com.pte.session.dto.response.SessionSummaryView;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Resolves session display names for Examiner read models; tolerant of an absent session module. */
class ExaminerSessionNames {

    private final SessionService sessionService;

    ExaminerSessionNames(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** Session display names for the given ids; absent when the session module is unavailable or has no match. */
    Map<UUID, String> namesOf(Collection<UUID> sessionPublicIds, UUID tenantId) {
        if (sessionService == null || sessionPublicIds.isEmpty()) {
            return Map.of();
        }
        return sessionService.findSummaries(Set.copyOf(sessionPublicIds), tenantId).stream()
                .filter(summary -> summary.name() != null)
                .collect(Collectors.toMap(SessionSummaryView::publicId, SessionSummaryView::name, (a, b) -> a));
    }
}
