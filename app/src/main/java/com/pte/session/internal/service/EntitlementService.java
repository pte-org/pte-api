package com.pte.session.internal.service;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.domain.enums.FormMode;
import com.pte.session.dto.response.EntitlementResponse;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.session.internal.exception.NotEntitledException;
import com.pte.session.internal.exception.ProctorNotAssignedException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.repository.ProctorAssignmentRepository;
import com.pte.session.internal.repository.FormAssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The trusted application-call surface for verifying a caller's standing
 * against a session before another module acts on their behalf.
 * {@link #checkEntitlement} backs attempt's attempt-create pull (Phase 07);
 * {@link #checkProctorAssignment} backs proctoring's session-open call
 * (Phase 09) — same shape, different actor.
 */
@Service
public class EntitlementService {

    private final ExamSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ProctorAssignmentRepository proctorAssignmentRepository;
    private final FormAssignmentRepository formAssignmentRepository;

    @Autowired
    public EntitlementService(ExamSessionRepository sessionRepository, EnrollmentRepository enrollmentRepository,
                              ProctorAssignmentRepository proctorAssignmentRepository,
                              FormAssignmentRepository formAssignmentRepository) {
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.proctorAssignmentRepository = proctorAssignmentRepository;
        this.formAssignmentRepository = formAssignmentRepository;
    }

    /** Compatibility constructor for focused session unit tests. */
    public EntitlementService(ExamSessionRepository sessionRepository, EnrollmentRepository enrollmentRepository,
                              ProctorAssignmentRepository proctorAssignmentRepository) {
        this(sessionRepository, enrollmentRepository, proctorAssignmentRepository, null);
    }

    @Transactional(readOnly = true)
    public EntitlementResponse checkEntitlement(UUID sessionPublicId, UUID studentPublicId) {
        ExamSession session = sessionRepository.findByPublicId(sessionPublicId)
                .orElseThrow(NotEntitledException::new);
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new NotEntitledException();
        }
        if (!enrollmentRepository.existsBySessionIdAndStudentPublicId(session.getId(), studentPublicId)) {
            throw new NotEntitledException();
        }
        UUID snapshotPublicId = session.getSnapshotPublicId();
        if (session.getFormMode() == FormMode.UNIQUE_FORM_PER_STUDENT && formAssignmentRepository != null) {
            snapshotPublicId = formAssignmentRepository.findBySessionIdAndStudentPublicId(session.getId(), studentPublicId)
                    .map(assignment -> assignment.getForm().getSnapshotPublicId())
                    .orElseThrow(NotEntitledException::new);
        }
        return new EntitlementResponse(session.getPublicId(), snapshotPublicId, session.getTenantId(),
                session.getOpensAt(), session.getClosesAt(), SessionMapper.toPolicy(session.getPolicy()));
    }

    @Transactional(readOnly = true)
    public ProctorAssignmentCheckResponse checkProctorAssignment(UUID sessionPublicId, UUID proctorPublicId) {
        ExamSession session = sessionRepository.findByPublicId(sessionPublicId)
                .orElseThrow(ProctorNotAssignedException::new);
        if (!proctorAssignmentRepository.existsBySessionIdAndProctorPublicId(session.getId(), proctorPublicId)) {
            throw new ProctorNotAssignedException();
        }
        return new ProctorAssignmentCheckResponse(session.getPublicId(), session.getTenantId());
    }

    /**
     * Tenant-ownership gate for scoring's host-triggered "score this session"
     * command (Phase 08) — same 404-not-403 shape as every other tenant check
     * in this codebase, never a false/empty result for "not owned".
     */
    @Transactional(readOnly = true)
    public void verifyHostAccess(UUID sessionPublicId, UUID tenantId) {
        sessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
    }
}
