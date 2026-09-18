package com.pte.enrollment.internal.service;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.enrollment.domain.LecturerAssignment;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.ProgramCoordinatorAssignment;
import com.pte.enrollment.domain.StudentClass;
import com.pte.enrollment.internal.exception.CoordinatorAlreadyAssignedException;
import com.pte.enrollment.internal.exception.CoordinatorAssignmentNotFoundException;
import com.pte.enrollment.internal.exception.LecturerAlreadyAssignedException;
import com.pte.enrollment.internal.exception.LecturerAssignmentNotFoundException;
import com.pte.enrollment.internal.dto.request.AssignCoordinatorRequest;
import com.pte.enrollment.internal.dto.request.AssignLecturerRequest;
import com.pte.enrollment.internal.dto.response.LecturerAssignmentResponse;
import com.pte.enrollment.internal.dto.response.ProgramCoordinatorAssignmentResponse;
import com.pte.enrollment.internal.repository.LecturerAssignmentRepository;
import com.pte.enrollment.internal.repository.ProgramCoordinatorAssignmentRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Owns both {@link LecturerAssignment} CRUD (Class-scoped) AND
 * {@link ProgramCoordinatorAssignment} CRUD (Program-scoped) â€” same
 * "one service, two+ sibling join-CRUDs" shape as
 * {@code scheduling.EnrollmentService}. Tenant scoping is delegated to
 * {@code ClassService.findOwned}/{@code ProgramService.findOwned} rather than
 * duplicated here â€” same reuse discipline as {@code SessionService.findOwned}
 * being shared by {@code scheduling.EnrollmentService}.
 */
@Service
public class AssignmentService {

    private final ClassService classService;
    private final ProgramService programService;
    private final LecturerAssignmentRepository lecturerAssignmentRepository;
    private final ProgramCoordinatorAssignmentRepository coordinatorAssignmentRepository;
    private final AuditLogService auditLogService;

    public AssignmentService(ClassService classService, ProgramService programService,
            LecturerAssignmentRepository lecturerAssignmentRepository,
            ProgramCoordinatorAssignmentRepository coordinatorAssignmentRepository,
            AuditLogService auditLogService) {
        this.classService = classService;
        this.programService = programService;
        this.lecturerAssignmentRepository = lecturerAssignmentRepository;
        this.coordinatorAssignmentRepository = coordinatorAssignmentRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Does not validate that {@code assigneePublicId} actually holds the
     * {@code EXAMINER} role at the DB level â€” iam and admin are separate
     * services/databases; mirrors how {@code ProctorAssignment.proctorPublicId}
     * is never validated either. The FE is responsible for only offering
     * correctly-roled users in the picker.
     */
    @Transactional
    public LecturerAssignmentResponse assignLecturer(UUID organizationPublicId, UUID programPublicId,
            UUID classPublicId, AssignLecturerRequest request, CurrentUser caller) {
        StudentClass studentClass = classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);

        LecturerAssignment assignment = new LecturerAssignment();
        assignment.setStudentClass(studentClass);
        assignment.setAssigneePublicId(request.assigneePublicId());
        assignment.setTenantId(caller.tenantId());

        LecturerAssignment saved;
        try {
            saved = lecturerAssignmentRepository.save(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw new LecturerAlreadyAssignedException();
        }        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_LECTURER_ASSIGNED, "Assigned Lecturer " + saved.getAssigneePublicId() + " to Class");
        return toLecturerResponse(saved, classPublicId);
    }

    @Transactional(readOnly = true)
    public List<LecturerAssignmentResponse> listLecturers(UUID organizationPublicId, UUID programPublicId,
            UUID classPublicId, CurrentUser caller) {
        classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        return lecturerAssignmentRepository.findByTenantIdAndStudentClass_PublicId(
                caller.tenantId(), classPublicId).stream()
                .map(assignment -> toLecturerResponse(assignment, classPublicId))
                .toList();
    }

    @Transactional
    public void unassignLecturer(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            UUID assignmentPublicId, CurrentUser caller) {
        classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        LecturerAssignment assignment = lecturerAssignmentRepository
                .findByPublicIdAndTenantId(assignmentPublicId, caller.tenantId())
                .orElseThrow(LecturerAssignmentNotFoundException::new);
        if (!assignment.getStudentClass().getPublicId().equals(classPublicId)) {
            throw new LecturerAssignmentNotFoundException();
        }
        lecturerAssignmentRepository.delete(assignment);        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_LECTURER_UNASSIGNED,
                "Unassigned Lecturer " + assignment.getAssigneePublicId() + " from Class");
    }

    @Transactional
    public ProgramCoordinatorAssignmentResponse assignCoordinator(UUID organizationPublicId, UUID programPublicId,
            AssignCoordinatorRequest request, CurrentUser caller) {
        Program program = programService.findOwned(organizationPublicId, programPublicId, caller);

        ProgramCoordinatorAssignment assignment = new ProgramCoordinatorAssignment();
        assignment.setProgram(program);
        assignment.setAssigneePublicId(request.assigneePublicId());
        assignment.setTenantId(caller.tenantId());

        ProgramCoordinatorAssignment saved;
        try {
            saved = coordinatorAssignmentRepository.save(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw new CoordinatorAlreadyAssignedException();
        }        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                EnrollmentConstants.EVENT_COORDINATOR_ASSIGNED,
                "Assigned Coordinator " + saved.getAssigneePublicId() + " to Program \"" + program.getName() + "\"");
        return toCoordinatorResponse(saved, programPublicId);
    }

    @Transactional(readOnly = true)
    public List<ProgramCoordinatorAssignmentResponse> listCoordinators(UUID organizationPublicId,
            UUID programPublicId, CurrentUser caller) {
        programService.findOwned(organizationPublicId, programPublicId, caller);
        return coordinatorAssignmentRepository.findByTenantIdAndProgram_PublicId(
                caller.tenantId(), programPublicId).stream()
                .map(assignment -> toCoordinatorResponse(assignment, programPublicId))
                .toList();
    }

    @Transactional
    public void unassignCoordinator(UUID organizationPublicId, UUID programPublicId, UUID assignmentPublicId,
            CurrentUser caller) {
        programService.findOwned(organizationPublicId, programPublicId, caller);
        ProgramCoordinatorAssignment assignment = coordinatorAssignmentRepository
                .findByPublicIdAndTenantId(assignmentPublicId, caller.tenantId())
                .orElseThrow(CoordinatorAssignmentNotFoundException::new);
        if (!assignment.getProgram().getPublicId().equals(programPublicId)) {
            throw new CoordinatorAssignmentNotFoundException();
        }
        coordinatorAssignmentRepository.delete(assignment);        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                EnrollmentConstants.EVENT_COORDINATOR_UNASSIGNED,
                "Unassigned Coordinator " + assignment.getAssigneePublicId() + " from Program");
    }

    private LecturerAssignmentResponse toLecturerResponse(LecturerAssignment assignment, UUID classPublicId) {
        return new LecturerAssignmentResponse(assignment.getPublicId(), classPublicId, assignment.getAssigneePublicId());
    }

    private ProgramCoordinatorAssignmentResponse toCoordinatorResponse(ProgramCoordinatorAssignment assignment,
            UUID programPublicId) {
        return new ProgramCoordinatorAssignmentResponse(assignment.getPublicId(), programPublicId,
                assignment.getAssigneePublicId());
    }
}
