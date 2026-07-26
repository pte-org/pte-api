package com.pte.scheduling.service;

import com.pte.common.security.CurrentUser;
import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.SessionComposition;
import com.pte.scheduling.domain.SnapshotRef;
import com.pte.scheduling.domain.exception.TaskTypeNotInSnapshotException;
import com.pte.scheduling.dto.request.CompositionItemRequest;
import com.pte.scheduling.dto.request.SetCompositionRequest;
import com.pte.scheduling.dto.response.SessionResponse;
import com.pte.scheduling.mapper.SessionMapper;
import com.pte.scheduling.repository.SnapshotRefRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sets a session's composition — the full-mock-vs-practice-subset mechanism
 * (ADR-001). Every requested task type must exist in the session's referenced
 * snapshot; this is what "composition rejects task types absent from the
 * snapshot" (phase-04 success criterion) enforces.
 */
@Service
public class CompositionService {

    private final SessionService sessionService;
    private final SnapshotRefRepository snapshotRefRepository;

    public CompositionService(SessionService sessionService, SnapshotRefRepository snapshotRefRepository) {
        this.sessionService = sessionService;
        this.snapshotRefRepository = snapshotRefRepository;
    }

    @Transactional
    public SessionResponse setComposition(UUID sessionPublicId, SetCompositionRequest request, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);
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
        SnapshotRef ref = snapshotRefRepository.findWithItemsBySnapshotPublicId(snapshotPublicId)
                .orElseThrow(TaskTypeNotInSnapshotException::new);
        return ref.getItems().stream().map(item -> item.getTaskType()).collect(Collectors.toSet());
    }

    private SessionComposition toEntity(CompositionItemRequest request) {
        SessionComposition item = new SessionComposition();
        item.setTaskType(request.taskType());
        item.setSection(request.section());
        item.setOrderIndex(request.orderIndex());
        item.setTimingOverrideSeconds(request.timingOverrideSeconds());
        return item;
    }
}
