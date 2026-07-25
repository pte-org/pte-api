package com.pte.scheduling.service;

import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.SessionComposition;
import com.pte.scheduling.domain.enums.SessionStatus;
import com.pte.scheduling.domain.exception.NotEntitledException;
import com.pte.scheduling.dto.response.CompositionItemResponse;
import com.pte.scheduling.dto.response.EntitlementResponse;
import com.pte.scheduling.repository.EnrollmentRepository;
import com.pte.scheduling.repository.ExamSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The internal service-to-service surface exam-delivery calls at attempt-create
 * (ADR-003 mTLS placeholder, phase-04/05 design constraint). Verifies the
 * EXPLICIT student identity exam-delivery passes — already resolved from that
 * student's own validated JWT, never trusted blindly — is actually enrolled
 * and the session is open, before releasing composition/snapshot metadata.
 */
@Service
public class EntitlementService {

    private final ExamSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;

    public EntitlementService(ExamSessionRepository sessionRepository, EnrollmentRepository enrollmentRepository) {
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional(readOnly = true)
    public EntitlementResponse checkEntitlement(UUID sessionPublicId, UUID studentPublicId) {
        ExamSession session = sessionRepository.findWithCompositionByPublicId(sessionPublicId)
                .orElseThrow(NotEntitledException::new);
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new NotEntitledException();
        }
        if (!enrollmentRepository.existsBySessionIdAndStudentPublicId(session.getId(), studentPublicId)) {
            throw new NotEntitledException();
        }
        List<CompositionItemResponse> composition = session.getComposition().stream()
                .map(this::toItem).toList();
        return new EntitlementResponse(session.getPublicId(), session.getSnapshotPublicId(), session.getTenantId(),
                session.getOpensAt(), session.getClosesAt(), composition);
    }

    private CompositionItemResponse toItem(SessionComposition item) {
        return new CompositionItemResponse(item.getTaskType(), item.getSection(), item.getOrderIndex(),
                item.getTimingOverrideSeconds());
    }
}
