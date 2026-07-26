package com.pte.scheduling.service;

import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.SessionComposition;
import com.pte.scheduling.domain.enums.SessionStatus;
import com.pte.scheduling.domain.exception.NotEntitledException;
import com.pte.scheduling.domain.exception.ProctorNotAssignedException;
import com.pte.scheduling.dto.response.CompositionItemResponse;
import com.pte.scheduling.dto.response.EntitlementResponse;
import com.pte.scheduling.dto.response.ProctorAssignmentCheckResponse;
import com.pte.scheduling.repository.EnrollmentRepository;
import com.pte.scheduling.repository.ExamSessionRepository;
import com.pte.scheduling.repository.ProctorAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The internal service-to-service surface for verifying a caller's standing
 * against a session before another service acts on their behalf (ADR-003 mTLS
 * placeholder). {@link #checkEntitlement} backs exam-delivery's attempt-create
 * pull (phase-04/05); {@link #checkProctorAssignment} backs proctor's
 * session-open call (phase-10) — same shape, different actor.
 */
@Service
public class EntitlementService {

    private final ExamSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ProctorAssignmentRepository proctorAssignmentRepository;

    public EntitlementService(ExamSessionRepository sessionRepository, EnrollmentRepository enrollmentRepository,
                              ProctorAssignmentRepository proctorAssignmentRepository) {
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.proctorAssignmentRepository = proctorAssignmentRepository;
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

    @Transactional(readOnly = true)
    public ProctorAssignmentCheckResponse checkProctorAssignment(UUID sessionPublicId, UUID proctorPublicId) {
        ExamSession session = sessionRepository.findWithCompositionByPublicId(sessionPublicId)
                .orElseThrow(ProctorNotAssignedException::new);
        if (!proctorAssignmentRepository.existsBySessionIdAndProctorPublicId(session.getId(), proctorPublicId)) {
            throw new ProctorNotAssignedException();
        }
        return new ProctorAssignmentCheckResponse(session.getPublicId(), session.getTenantId());
    }

    private CompositionItemResponse toItem(SessionComposition item) {
        return new CompositionItemResponse(item.getTaskType(), item.getSection(), item.getOrderIndex(),
                item.getTimingOverrideSeconds());
    }
}
