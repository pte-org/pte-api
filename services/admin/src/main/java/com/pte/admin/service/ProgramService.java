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
import com.pte.admin.domain.exception.ProgramNameAlreadyUsedException;
import com.pte.admin.domain.exception.ProgramNotFoundException;
import com.pte.admin.dto.request.CreateProgramRequest;
import com.pte.admin.dto.request.UpdateProgramRequest;
import com.pte.admin.dto.response.ProgramResponse;
import com.pte.admin.mapper.ProgramMapper;
import com.pte.admin.messaging.outbox.OutboxWriter;
import com.pte.admin.repository.OrganizationRepository;
import com.pte.admin.repository.ProgramRepository;
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
    private final OutboxWriter outboxWriter;

    public ProgramService(ProgramRepository programRepository, OrganizationRepository organizationRepository,
            OutboxWriter outboxWriter) {
        this.programRepository = programRepository;
        this.organizationRepository = organizationRepository;
        this.outboxWriter = outboxWriter;
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
        Program program = loadOwned(organizationPublicId, programPublicId, caller);
        return ProgramMapper.toResponse(program, organizationPublicId);
    }

    @Transactional
    public ProgramResponse update(UUID organizationPublicId, UUID programPublicId, UpdateProgramRequest request,
            CurrentUser caller) {
        Program program = loadOwned(organizationPublicId, programPublicId, caller);
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
     * out of {@link #list}. No active-children check yet: {@code StudentClass}
     * doesn't exist until Phase 3, which retrofits the guard directly here.
     */
    @Transactional
    public ProgramResponse archive(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
        Program program = loadOwned(organizationPublicId, programPublicId, caller);
        if (program.isDeleted()) {
            return ProgramMapper.toResponse(program, organizationPublicId);
        }
        // Phase 3 retrofits an active-children guard here (reject if any non-deleted StudentClass exists under this Program).
        program.setDeleted(true);

        outboxWriter.write(AdminConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                AdminConstants.EVENT_PROGRAM_ARCHIVED,
                new ProgramArchivedEvent(programPublicId, organizationPublicId, caller.tenantId()), caller.tenantId());
        return ProgramMapper.toResponse(program, organizationPublicId);
    }

    private ProgramResponse changeStatus(UUID organizationPublicId, UUID programPublicId, CurrentUser caller,
            ProgramStatus target) {
        Program program = loadOwned(organizationPublicId, programPublicId, caller);
        if (program.getStatus() == target) {
            return ProgramMapper.toResponse(program, organizationPublicId);
        }
        program.setStatus(target);

        outboxWriter.write(AdminConstants.AGGREGATE_PROGRAM, programPublicId.toString(),
                AdminConstants.EVENT_PROGRAM_STATUS_CHANGED,
                new ProgramStatusChangedEvent(programPublicId, organizationPublicId, caller.tenantId(), target.name()),
                caller.tenantId());
        return ProgramMapper.toResponse(program, organizationPublicId);
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
    private Program loadOwned(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
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
