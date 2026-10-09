package com.pte.session.internal.service;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.FormMode;
import com.pte.session.dto.response.EntitlementResponse;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.session.internal.exception.InvalidSessionCodeException;
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

import java.time.Clock;
import java.util.Locale;
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

    /** Matches the {@code session_code} column; no generated code is longer. */
    private static final int MAX_SESSION_CODE_LENGTH = 24;

    private final ExamSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ProctorAssignmentRepository proctorAssignmentRepository;
    private final FormAssignmentRepository formAssignmentRepository;
    private final Clock clock;

    @Autowired
    public EntitlementService(ExamSessionRepository sessionRepository, EnrollmentRepository enrollmentRepository,
                              ProctorAssignmentRepository proctorAssignmentRepository,
                              FormAssignmentRepository formAssignmentRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.proctorAssignmentRepository = proctorAssignmentRepository;
        this.formAssignmentRepository = formAssignmentRepository;
        this.clock = clock;
    }

    /** Compatibility constructor for focused session unit tests. */
    public EntitlementService(ExamSessionRepository sessionRepository, EnrollmentRepository enrollmentRepository,
                              ProctorAssignmentRepository proctorAssignmentRepository, Clock clock) {
        this(sessionRepository, enrollmentRepository, proctorAssignmentRepository, null, clock);
    }

    /**
     * Gates every new attempt (preflight and start). Enrollment is checked
     * before status and time so a non-enrolled caller always gets the same
     * NOT_ENTITLED and learns nothing about the session's schedule.
     */
    @Transactional(readOnly = true)
    public EntitlementResponse checkEntitlement(UUID sessionPublicId, UUID studentPublicId) {
        ExamSession session = sessionRepository.findByPublicId(sessionPublicId)
                .orElseThrow(NotEntitledException::new);
        if (!isEnrolled(session, studentPublicId)) {
            throw new NotEntitledException();
        }
        SessionEntryGate.requireOpen(session);
        SessionEntryGate.requireWithinWindow(session, clock.instant());
        UUID snapshotPublicId = session.getSnapshotPublicId();
        if (session.getFormMode() == FormMode.UNIQUE_FORM_PER_STUDENT && formAssignmentRepository != null) {
            snapshotPublicId = formAssignmentRepository.findBySessionIdAndStudentPublicId(session.getId(), studentPublicId)
                    .map(assignment -> assignment.getForm().getSnapshotPublicId())
                    .orElseThrow(NotEntitledException::new);
        }
        return new EntitlementResponse(session.getPublicId(), snapshotPublicId, session.getTenantId(),
                session.getOpensAt(), session.getClosesAt(), SessionMapper.toPolicy(session.getPolicy(),
                        session.getExamMode(), session.getExamMode() == null),
                session.getExamMode() == null ? "OFFICIAL_EXAM" : session.getExamMode().name());
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

    /**
     * Exchanges a student-typed exam code for the session's publicId. Unknown
     * code, another tenant's code, a deleted session, no tenant, and not
     * enrolled all raise the same 404 so the response never tells which codes
     * exist. Status is deliberately not checked: preflight stays the OPEN gate.
     */
    @Transactional(readOnly = true)
    public UUID resolveSessionCode(String rawCode, UUID tenantId, UUID studentPublicId) {
        String code = normalizeSessionCode(rawCode);
        if (tenantId == null) {
            throw new SessionNotFoundException();
        }
        ExamSession session = sessionRepository.findBySessionCodeAndTenantIdAndDeletedFalse(code, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (!isEnrolled(session, studentPublicId)) {
            throw new SessionNotFoundException();
        }
        return session.getPublicId();
    }

    private boolean isEnrolled(ExamSession session, UUID studentPublicId) {
        return enrollmentRepository.existsBySessionIdAndStudentPublicId(session.getId(), studentPublicId);
    }

    /** Checks the input's shape only, before any lookup, so a 400 never depends on stored data. */
    private static String normalizeSessionCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty() || code.length() > MAX_SESSION_CODE_LENGTH) {
            throw new InvalidSessionCodeException();
        }
        return code;
    }
}
