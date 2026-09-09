package com.pte.admin.service;

import com.pte.admin.constant.AdminConstants;
import com.pte.admin.domain.LecturerAssignment;
import com.pte.admin.domain.Program;
import com.pte.admin.domain.ProgramCoordinatorAssignment;
import com.pte.admin.domain.StudentClass;
import com.pte.admin.domain.event.CoordinatorAssignedEvent;
import com.pte.admin.domain.event.CoordinatorUnassignedEvent;
import com.pte.admin.domain.event.LecturerAssignedEvent;
import com.pte.admin.domain.event.LecturerUnassignedEvent;
import com.pte.admin.domain.exception.CoordinatorAlreadyAssignedException;
import com.pte.admin.domain.exception.CoordinatorAssignmentNotFoundException;
import com.pte.admin.domain.exception.LecturerAlreadyAssignedException;
import com.pte.admin.domain.exception.LecturerAssignmentNotFoundException;
import com.pte.admin.dto.request.AssignCoordinatorRequest;
import com.pte.admin.dto.request.AssignLecturerRequest;
import com.pte.admin.dto.response.LecturerAssignmentResponse;
import com.pte.admin.dto.response.ProgramCoordinatorAssignmentResponse;
import com.pte.admin.messaging.outbox.OutboxWriter;
import com.pte.admin.repository.LecturerAssignmentRepository;
import com.pte.admin.repository.ProgramCoordinatorAssignmentRepository;
import com.pte.common.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Owns both {@link LecturerAssignment} CRUD (Class-scoped) AND
 * {@link ProgramCoordinatorAssignment} CRUD (Program-scoped) — same
 * "one service, two+ sibling join-CRUDs" shape as
 * {@code scheduling.EnrollmentService}. Tenant scoping is delegated to
 * {@code ClassService.findOwned}/{@code ProgramService.findOwned} rather than
 * duplicated here — same reuse discipline as {@code SessionService.findOwned}
 * being shared by {@code scheduling.EnrollmentService}.
 */
@Service
public class AssignmentService {

    private final ClassService classService;
    private final ProgramService programService;
    private final LecturerAssignmentRepository lecturerAssignmentRepository;
    private final ProgramCoordinatorAssignmentRepository coordinatorAssignmentRepository;
    private final OutboxWriter outboxWriter;

    public AssignmentService(ClassService classService, ProgramService programService,
            LecturerAssignmentRepository lecturerAssignmentRepository,
            ProgramCoordinatorAssignmentRepository coordinatorAssignmentRepository, OutboxWriter outboxWriter) {
        this.classService = classService;
        this.programService = programService;
        this.lecturerAssignmentRepository = lecturerAssignmentRepository;
        this.coordinatorAssignmentRepository = coordinatorAssignmentRepository;
        this.outboxWriter = outboxWriter;
    }

    /**
     * Does not validate that {@code assigneePublicId} actually holds the
     * {@code LECTURER} role at the DB level — iam and admin are separate
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
        }

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_LECTURER_ASSIGNED,
                new LecturerAssignedEvent(saved.getPublicId(), classPublicId, saved.getAssigneePublicId(),
                        caller.tenantId()),
                caller.tenantId());
        return toLecturerResponse(saved, classPublicId);
    }

    @Transactional(readOnly = true)
    public List<LecturerAssignmentResponse> listLecturers(UUID organizationPublicId, UUID programPublicId,
            UUID classPublicId, CurrentUser caller) {
        classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        return lecturerAssignmentRepository.findByStudentClass_PublicId(classPublicId).stream()
                .map(assignment -> toLecturerResponse(assignment, classPublicId))
                .toList();
    }

    @Transactional
    public void unassignLecturer(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            UUID assignmentPublicId, CurrentUser caller) {
        classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        LecturerAssignment assignment = lecturerAssignmentRepository.findByPublicId(assignmentPublicId)
                .orElseThrow(LecturerAssignmentNotFoundException::new);
        if (!assignment.getStudentClass().getPublicId().equals(classPublicId)) {
            throw new LecturerAssignmentNotFoundException();
        }
        lecturerAssignmentRepository.delete(assignment);

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_LECTURER_UNASSIGNED,
                new LecturerUnassignedEvent(assignmentPublicId, classPublicId, assignment.getAssigneePublicId(),
                        caller.tenantId()),
                caller.tenantId());
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
        }

        outboxWriter.write(AdminConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                AdminConstants.EVENT_COORDINATOR_ASSIGNED,
                new CoordinatorAssignedEvent(saved.getPublicId(), programPublicId, saved.getAssigneePublicId(),
                        caller.tenantId()),
                caller.tenantId());
        return toCoordinatorResponse(saved, programPublicId);
    }

    @Transactional(readOnly = true)
    public List<ProgramCoordinatorAssignmentResponse> listCoordinators(UUID organizationPublicId,
            UUID programPublicId, CurrentUser caller) {
        programService.findOwned(organizationPublicId, programPublicId, caller);
        return coordinatorAssignmentRepository.findByProgram_PublicId(programPublicId).stream()
                .map(assignment -> toCoordinatorResponse(assignment, programPublicId))
                .toList();
    }

    @Transactional
    public void unassignCoordinator(UUID organizationPublicId, UUID programPublicId, UUID assignmentPublicId,
            CurrentUser caller) {
        programService.findOwned(organizationPublicId, programPublicId, caller);
        ProgramCoordinatorAssignment assignment = coordinatorAssignmentRepository.findByPublicId(assignmentPublicId)
                .orElseThrow(CoordinatorAssignmentNotFoundException::new);
        if (!assignment.getProgram().getPublicId().equals(programPublicId)) {
            throw new CoordinatorAssignmentNotFoundException();
        }
        coordinatorAssignmentRepository.delete(assignment);

        outboxWriter.write(AdminConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                AdminConstants.EVENT_COORDINATOR_UNASSIGNED,
                new CoordinatorUnassignedEvent(assignmentPublicId, programPublicId, assignment.getAssigneePublicId(),
                        caller.tenantId()),
                caller.tenantId());
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
