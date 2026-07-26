package com.pte.scheduling.mapper;

import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.SessionComposition;
import com.pte.scheduling.dto.response.CompositionItemResponse;
import com.pte.scheduling.dto.response.SessionResponse;

import java.util.List;

public final class SessionMapper {

    private SessionMapper() {
    }

    public static SessionResponse toResponse(ExamSession session) {
        List<CompositionItemResponse> composition = session.getComposition().stream()
                .map(SessionMapper::toItem)
                .toList();
        return new SessionResponse(
                session.getPublicId(),
                session.getName(),
                session.getTenantId(),
                session.getSnapshotPublicId(),
                session.getOpensAt(),
                session.getClosesAt(),
                session.getStatus().name(),
                composition);
    }

    private static CompositionItemResponse toItem(SessionComposition item) {
        return new CompositionItemResponse(item.getTaskType(), item.getSection(), item.getOrderIndex(),
                item.getTimingOverrideSeconds());
    }
}
