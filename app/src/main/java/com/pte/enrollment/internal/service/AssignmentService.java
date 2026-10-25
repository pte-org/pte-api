package com.pte.enrollment.internal.service;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.enrollment.domain.ClassLecturer;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.internal.exception.LecturerAlreadyAssignedException;
import com.pte.enrollment.internal.exception.LecturerAssignmentNotFoundException;
import com.pte.enrollment.internal.dto.request.AssignLecturerRequest;
import com.pte.enrollment.internal.dto.response.LecturerAssignmentResponse;
import com.pte.enrollment.internal.repository.ClassLecturerRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Owns {@link ClassLecturer} CRUD (Class-scoped). Tenant scoping is
 * delegated to {@code ClassService.findOwned} rather than duplicated here â€”
 * same reuse discipline as {@code SessionService.findOwned} being shared by
 * {@code scheduling.EnrollmentService}.
 */
@Service
public class AssignmentService {

    private final ClassService classService;
    private final ClassLecturerRepository classLecturerRepository;
    private final AuditLogService auditLogService;

    public AssignmentService(ClassService classService,
            ClassLecturerRepository classLecturerRepository,
            AuditLogService auditLogService) {
        this.classService = classService;
        this.classLecturerRepository = classLecturerRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Does not validate that {@code assigneePublicId} actually holds the
     * {@code EXAMINER} role at the DB level â€” iam and admin are separate
     * services/databases; mirrors how {@code ExamSessionProctor.proctorPublicId}
     * is never validated either. The FE is responsible for only offering
     * correctly-roled users in the picker.
     */
    @Transactional
    public LecturerAssignmentResponse assignLecturer(UUID organizationPublicId, UUID programPublicId,
            UUID classPublicId, AssignLecturerRequest request, CurrentUser caller) {
        TrainingClass trainingClass = classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);

        ClassLecturer assignment = new ClassLecturer();
        assignment.setTrainingClass(trainingClass);
        assignment.setAssigneePublicId(request.assigneePublicId());
        assignment.setOrganizationId(caller.tenantId());

        ClassLecturer saved;
        try {
            saved = classLecturerRepository.save(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw new LecturerAlreadyAssignedException();
        }
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_LECTURER_ASSIGNED, "Assigned lecturer to Class");
        return toLecturerResponse(saved, classPublicId);
    }

    @Transactional(readOnly = true)
    public List<LecturerAssignmentResponse> listLecturers(UUID organizationPublicId, UUID programPublicId,
            UUID classPublicId, CurrentUser caller) {
        classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        return classLecturerRepository.findByOrganizationIdAndTrainingClass_PublicId(
                caller.tenantId(), classPublicId).stream()
                .map(assignment -> toLecturerResponse(assignment, classPublicId))
                .toList();
    }

    @Transactional
    public void unassignLecturer(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            UUID assignmentPublicId, CurrentUser caller) {
        classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        ClassLecturer assignment = classLecturerRepository
                .findByPublicIdAndOrganizationId(assignmentPublicId, caller.tenantId())
                .orElseThrow(LecturerAssignmentNotFoundException::new);
        if (!assignment.getTrainingClass().getPublicId().equals(classPublicId)) {
            throw new LecturerAssignmentNotFoundException();
        }
        classLecturerRepository.delete(assignment);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_LECTURER_UNASSIGNED,
                "Unassigned lecturer from Class");
    }

    private LecturerAssignmentResponse toLecturerResponse(ClassLecturer assignment, UUID classPublicId) {
        return new LecturerAssignmentResponse(assignment.getPublicId(), classPublicId, assignment.getAssigneePublicId());
    }
}
