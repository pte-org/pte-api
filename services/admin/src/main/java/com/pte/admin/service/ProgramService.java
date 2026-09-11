package com.pte.admin.service;

import com.pte.admin.constant.AdminConstants;
import com.pte.admin.domain.Organization;
import com.pte.admin.domain.Program;
import com.pte.admin.domain.enums.ProgramStatus;
import com.pte.admin.domain.event.ProgramArchivedEvent;
import com.pte.admin.domain.event.ProgramCreatedEvent;
import com.pte.admin.domain.event.ProgramStatusChangedEvent;
import com.pte.admin.domain.event.ProgramUpdatedEvent;
import com.pte.admin.domain.exception.OrganizationNotFoundException;
import com.pte.admin.domain.exception.ProgramHasActiveClassesException;
import com.pte.admin.domain.exception.ProgramNameAlreadyUsedException;
import com.pte.admin.domain.exception.ProgramNotFoundException;
import com.pte.admin.dto.request.CreateProgramRequest;
import com.pte.admin.dto.request.UpdateProgramRequest;
import com.pte.admin.dto.response.ClassStudentCountResponse;
import com.pte.admin.dto.response.ProgramDashboardResponse;
import com.pte.admin.dto.response.ProgramResponse;
import com.pte.admin.mapper.ProgramMapper;
import com.pte.admin.messaging.outbox.OutboxWriter;
import com.pte.admin.repository.OrganizationRepository;
import com.pte.admin.repository.ProgramRepository;
import com.pte.admin.repository.StudentClassRepository;
import com.pte.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Program (Khối/Khóa) governance under a Host's own Organization. First
 * self-service surface in `admin`: every method resolves tenant scope from
 * {@code caller.tenantId()} (JWT), never a path param — mirrors
 * {@code scheduling.SessionService.findOwned}, not
 * {@code OrganizationService.loadUnderTenant}'s path-param-trusting sibling
 * (that one is safe only because it's platform-admin-only).
 */
@Service
public class ProgramService {

    private final ProgramRepository programRepository;
    private final OrganizationRepository organizationRepository;
    private final StudentClassRepository studentClassRepository;
    private final OutboxWriter outboxWriter;
    private final AuditLogService auditLogService;

    public ProgramService(ProgramRepository programRepository, OrganizationRepository organizationRepository,
            StudentClassRepository studentClassRepository, OutboxWriter outboxWriter,
            AuditLogService auditLogService) {
        this.programRepository = programRepository;
        this.organizationRepository = organizationRepository;
        this.studentClassRepository = studentClassRepository;
        this.outboxWriter = outboxWriter;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ProgramResponse create(UUID organizationPublicId, CreateProgramRequest request, CurrentUser caller) {
        Organization organization = loadOrganizationOwned(organizationPublicId, caller);
        if (programRepository.existsByOrganization_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                organizationPublicId, request.name())) {
            throw new ProgramNameAlreadyUsedException();
        }

        Program program = new Program();
        program.setOrganization(organization);
        program.setName(request.name());
        program.setDescription(request.description());
        program.setStartDate(request.startDate());
        program.setEndDate(request.endDate());
        Program saved = programRepository.save(program);

        outboxWriter.write(AdminConstants.AGGREGATE_PROGRAM, saved.getPublicId().toString(),
                AdminConstants.EVENT_PROGRAM_CREATED,
                new ProgramCreatedEvent(saved.getPublicId(), organizationPublicId, caller.tenantId(), saved.getName()),
                caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_PROGRAM, saved.getPublicId().toString(),
                AdminConstants.EVENT_PROGRAM_CREATED, "Created Program \"" + saved.getName() + "\"");
        return ProgramMapper.toResponse(saved, organizationPublicId);
    }

    @Transactional(readOnly = true)
    public List<ProgramResponse> list(UUID organizationPublicId, CurrentUser caller) {
        loadOrganizationOwned(organizationPublicId, caller);
        return programRepository.findByOrganization_PublicIdAndDeletedFalseOrderByCreatedAtAsc(organizationPublicId)
                .stream()
                .map(program -> ProgramMapper.toResponse(program, organizationPublicId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramResponse get(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
        Program program = findOwned(organizationPublicId, programPublicId, caller);
        return ProgramMapper.toResponse(program, organizationPublicId);
    }

    @Transactional
    public ProgramResponse update(UUID organizationPublicId, UUID programPublicId, UpdateProgramRequest request,
            CurrentUser caller) {
        Program program = findOwned(organizationPublicId, programPublicId, caller);
        if (!program.getName().equalsIgnoreCase(request.name())
                && programRepository.existsByOrganization_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                        organizationPublicId, request.name())) {
            throw new ProgramNameAlreadyUsedException();
        }
        program.setName(request.name());
        program.setDescription(request.description());
        program.setStartDate(request.startDate());
        program.setEndDate(request.endDate());

        outboxWriter.write(AdminConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                AdminConstants.EVENT_PROGRAM_UPDATED,
                new ProgramUpdatedEvent(programPublicId, organizationPublicId, caller.tenantId()), caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                AdminConstants.EVENT_PROGRAM_UPDATED, "Updated Program \"" + program.getName() + "\"");
        return ProgramMapper.toResponse(program, organizationPublicId);
    }

    @Transactional
    public ProgramResponse activate(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
        return changeStatus(organizationPublicId, programPublicId, caller, ProgramStatus.ACTIVE);
    }

    @Transactional
    public ProgramResponse deactivate(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
        return changeStatus(organizationPublicId, programPublicId, caller, ProgramStatus.INACTIVE);
    }

    @Transactional
    public ProgramResponse suspend(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
        return changeStatus(organizationPublicId, programPublicId, caller, ProgramStatus.SUSPENDED);
    }

    /**
     * Visibility/lifecycle flag via the inherited {@code deleted} column, NOT a
     * hard delete — archived Programs stay fetchable by {@link #get} but drop
     * out of {@link #list}. Rejects if any non-deleted {@code StudentClass}
     * still exists under this Program — the Host must archive/move the Classes
     * first (Phase 3 retrofit).
     */
    @Transactional
    public ProgramResponse archive(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
        Program program = findOwned(organizationPublicId, programPublicId, caller);
        if (program.isDeleted()) {
            return ProgramMapper.toResponse(program, organizationPublicId);
        }
        if (studentClassRepository.existsByProgram_PublicIdAndDeletedFalse(programPublicId)) {
            throw new ProgramHasActiveClassesException();
        }
        program.setDeleted(true);

        outboxWriter.write(AdminConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                AdminConstants.EVENT_PROGRAM_ARCHIVED,
                new ProgramArchivedEvent(programPublicId, organizationPublicId, caller.tenantId()), caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                AdminConstants.EVENT_PROGRAM_ARCHIVED, "Archived Program \"" + program.getName() + "\"");
        return ProgramMapper.toResponse(program, organizationPublicId);
    }

    private ProgramResponse changeStatus(UUID organizationPublicId, UUID programPublicId, CurrentUser caller,
            ProgramStatus target) {
        Program program = findOwned(organizationPublicId, programPublicId, caller);
        if (program.getStatus() == target) {
            return ProgramMapper.toResponse(program, organizationPublicId);
        }
        program.setStatus(target);

        outboxWriter.write(AdminConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                AdminConstants.EVENT_PROGRAM_STATUS_CHANGED,
                new ProgramStatusChangedEvent(programPublicId, organizationPublicId, caller.tenantId(), target.name()),
                caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                AdminConstants.EVENT_PROGRAM_STATUS_CHANGED, "Changed Program status to " + target.name());
        return ProgramMapper.toResponse(program, organizationPublicId);
    }

    /**
     * One grouped query (see {@code StudentClassRepository.countStudentsByClassForProgram})
     * for class/student counts under this Program — never client-side
     * aggregation over a potentially large membership list, and never N+1.
     */
    @Transactional(readOnly = true)
    public ProgramDashboardResponse getDashboard(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
        findOwned(organizationPublicId, programPublicId, caller);
        List<ClassStudentCountResponse> classes = studentClassRepository.countStudentsByClassForProgram(programPublicId);
        long studentCount = classes.stream().mapToLong(ClassStudentCountResponse::studentCount).sum();
        return new ProgramDashboardResponse(programPublicId, classes.size(), studentCount, classes);
    }

    private Organization loadOrganizationOwned(UUID organizationPublicId, CurrentUser caller) {
        Organization organization = organizationRepository.findByPublicId(organizationPublicId)
                .orElseThrow(OrganizationNotFoundException::new);
        if (!organization.getTenant().getPublicId().equals(caller.tenantId())) {
            throw new OrganizationNotFoundException();
        }
        return organization;
    }

    /**
     * Loads a Program strictly by (organizationPublicId, programPublicId, caller's
     * tenant) — a program id that exists but belongs to a different Organization
     * or a different tenant than the caller is treated as not-found, never
     * silently served (same "wrong scope looks like not-found" pattern as
     * {@code OrganizationService.loadUnderTenant}).
     */
    Program findOwned(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
        Program program = programRepository.findByPublicId(programPublicId)
                .orElseThrow(ProgramNotFoundException::new);
        Organization organization = program.getOrganization();
        if (!organization.getPublicId().equals(organizationPublicId)
                || !organization.getTenant().getPublicId().equals(caller.tenantId())) {
            throw new ProgramNotFoundException();
        }
        return program;
    }
}
