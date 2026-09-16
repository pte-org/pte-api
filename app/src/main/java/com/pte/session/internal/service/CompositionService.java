package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.SessionComposition;
import com.pte.session.internal.dto.request.CompositionItemRequest;
import com.pte.session.internal.dto.request.SetCompositionRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.TaskTypeNotInSnapshotException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sets a session's composition — the full-mock-vs-practice-subset mechanism.
 * Every requested task type must exist in the session's referenced snapshot,
 * read fresh from {@link AssessmentService#getSummary} each call (no local
 * cache/ref table — an in-process call is cheap enough that caching would
 * only reintroduce the staleness risk the monolith exists to remove).
 */
@Service
public class CompositionService {

    private final SessionLifecycleService sessionLifecycleService;
    private final AssessmentService assessmentService;

    public CompositionService(SessionLifecycleService sessionLifecycleService, AssessmentService assessmentService) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.assessmentService = assessmentService;
    }

    @Transactional
    public SessionResponse setComposition(UUID sessionPublicId, SetCompositionRequest request, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        Set<String> availableTaskTypes = availableTaskTypes(session.getSnapshotPublicId());

        session.getComposition().clear();
        request.items().forEach(item -> {
            if (!availableTaskTypes.contains(item.taskType())) {
                throw new TaskTypeNotInSnapshotException();
            }
            session.addCompositionItem(toEntity(item));
        });
        return SessionMapper.toResponse(session);
    }

    private Set<String> availableTaskTypes(UUID snapshotPublicId) {
        SnapshotResponse snapshot = assessmentService.getSummary(snapshotPublicId);
        return snapshot.items().stream().map(SnapshotResponse.Item::taskType).collect(Collectors.toSet());
    }

    private SessionComposition toEntity(CompositionItemRequest request) {
        SessionComposition item = new SessionComposition();
        item.setTaskType(request.taskType());
        item.setSection(request.section());
        item.setOrderIndex(request.orderIndex());
        item.setTimingOverrideSeconds(request.timingOverrideSeconds());
        item.setMaxPlayCount(request.maxPlayCount());
        return item;
    }
}
