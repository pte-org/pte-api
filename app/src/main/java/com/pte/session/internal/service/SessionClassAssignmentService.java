package com.pte.session.internal.service;

import com.pte.enrollment.EnrollmentModuleService;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.SessionClassAssignment;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.dto.request.BulkEnrollRequest;
import com.pte.session.internal.dto.response.SessionClassAssignmentResponse;
import com.pte.session.internal.exception.ClassAssignmentNotAllowedException;
import com.pte.session.internal.exception.ClassAssignmentNotFoundException;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.SessionClassAssignmentRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Assign/unassign a Class to/from a SCHEDULED session — the mechanism that
 * turns "a Class exists" into "its students may sit this exam" (Plan B,
 * Phase 4). Roster effect is delegated to {@link EnrollmentService#bulkEnroll}
 * (assign) / a direct {@code Enrollment} delete (unassign); this service only
 * tracks WHICH Classes are assigned.
 */
@Service
public class SessionClassAssignmentService {

    private final SessionLifecycleService sessionLifecycleService;
    private final EnrollmentService enrollmentService;
    private final EnrollmentModuleService enrollmentModuleService;
    private final SessionClassAssignmentRepository assignmentRepository;
    private final EnrollmentRepository enrollmentRepository;

    public SessionClassAssignmentService(SessionLifecycleService sessionLifecycleService,
                                         EnrollmentService enrollmentService,
                                         EnrollmentModuleService enrollmentModuleService,
                                         SessionClassAssignmentRepository assignmentRepository,
                                         EnrollmentRepository enrollmentRepository) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.enrollmentService = enrollmentService;
        this.enrollmentModuleService = enrollmentModuleService;
        this.assignmentRepository = assignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * Always forwards the Class's CURRENT membership to {@code bulkEnroll} —
     * including when the Class was already assigned before — so a Class that
     * gained members since the last assign gets those new members enrolled
     * too. {@code bulkEnroll} itself skips students already enrolled, so this
     * is safe to call every time, never just on the first assign.
     */
    @Transactional
    public SessionClassAssignmentResponse assign(UUID sessionPublicId, UUID classPublicId, CurrentUser caller) {
        ExamSession session = requireScheduled(sessionPublicId, caller);
        List<UUID> studentPublicIds = enrollmentModuleService.findActiveStudentPublicIds(caller.tenantId(), classPublicId);
        enrollmentService.bulkEnroll(sessionPublicId, new BulkEnrollRequest(studentPublicIds), caller);

        if (!assignmentRepository.existsBySessionIdAndClassPublicId(session.getId(), classPublicId)) {
            SessionClassAssignment assignment = new SessionClassAssignment();
            assignment.setSession(session);
            assignment.setTenantId(caller.tenantId());
            assignment.setClassPublicId(classPublicId);
            assignmentRepository.save(assignment);
        }
        return new SessionClassAssignmentResponse(sessionPublicId, classPublicId);
    }

    @Transactional(readOnly = true)
    public List<SessionClassAssignmentResponse> list(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        return assignmentRepository.findBySessionId(session.getId()).stream()
                .map(a -> new SessionClassAssignmentResponse(sessionPublicId, a.getClassPublicId()))
                .toList();
    }

    /**
     * Removes the {@code Enrollment} rows of the Class's CURRENT members only —
     * a student who has since moved to a different Class is no longer found by
     * {@link EnrollmentModuleService#findActiveStudentPublicIds} for this
     * classPublicId, so their enrollment (made under the old membership) is
     * left untouched (see {@code class_memberships.student_public_id} UNIQUE
     * constraint, V4__enrollment.sql:62 — a student belongs to at most one
     * Class, so there is no "still covered by another assigned Class" set to
     * subtract).
     */
    @Transactional
    public void unassign(UUID sessionPublicId, UUID classPublicId, CurrentUser caller) {
        ExamSession session = requireScheduled(sessionPublicId, caller);
        SessionClassAssignment assignment = assignmentRepository
                .findBySessionIdAndClassPublicId(session.getId(), classPublicId)
                .orElseThrow(ClassAssignmentNotFoundException::new);

        List<UUID> studentPublicIds = enrollmentModuleService.findActiveStudentPublicIds(caller.tenantId(), classPublicId);
        if (!studentPublicIds.isEmpty()) {
            enrollmentRepository.deleteBySessionIdAndStudentPublicIdIn(session.getId(), studentPublicIds);
        }
        assignmentRepository.delete(assignment);
    }

    private ExamSession requireScheduled(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new ClassAssignmentNotAllowedException();
        }
        return session;
    }
}
