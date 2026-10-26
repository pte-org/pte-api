package com.pte.enrollment.internal.service;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.tenancy.domain.Organization;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.internal.exception.ProgramNotFoundException;
import com.pte.enrollment.internal.exception.StudentClassNameAlreadyUsedException;
import com.pte.enrollment.internal.exception.StudentClassNotFoundException;
import com.pte.enrollment.internal.dto.request.CreateClassRequest;
import com.pte.enrollment.internal.dto.request.UpdateClassRequest;
import com.pte.enrollment.internal.dto.response.ClassResponse;
import com.pte.enrollment.internal.mapper.TrainingClassMapper;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.TrainingClassRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Create/read/rename of a {@link TrainingClass} and the tenant-scoped lookup the other
 * class services share. Every method resolves tenant scope from {@code caller.tenantId()}
 * (JWT), never a path param — same pattern as {@code ProgramService}.
 */
@Service
public class ClassService {

    private final TrainingClassRepository trainingClassRepository;
    private final ProgramRepository programRepository;
    private final AuditLogService auditLogService;

    public ClassService(TrainingClassRepository trainingClassRepository, ProgramRepository programRepository,
            AuditLogService auditLogService) {
        this.trainingClassRepository = trainingClassRepository;
        this.programRepository = programRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ClassResponse create(UUID organizationPublicId, UUID programPublicId, CreateClassRequest request,
            CurrentUser caller) {
        Program program = loadProgramOwned(organizationPublicId, programPublicId, caller);
        if (trainingClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                programPublicId, request.name())) {
            throw new StudentClassNameAlreadyUsedException();
        }

        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setProgram(program);
        trainingClass.setName(request.name());
        TrainingClass saved = trainingClassRepository.save(trainingClass);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, saved.getPublicId().toString(),
                EnrollmentConstants.EVENT_CLASS_CREATED, "Created Class \"" + saved.getName() + "\"");
        return TrainingClassMapper.toResponse(saved, programPublicId);
    }

    @Transactional(readOnly = true)
    public List<ClassResponse> list(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
        loadProgramOwned(organizationPublicId, programPublicId, caller);
        return trainingClassRepository.findByProgram_PublicIdAndDeletedFalseOrderByCreatedAtAsc(programPublicId)
                .stream()
                .map(trainingClass -> TrainingClassMapper.toResponse(trainingClass, programPublicId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassResponse get(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller) {
        TrainingClass trainingClass = findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        return TrainingClassMapper.toResponse(trainingClass, programPublicId);
    }

    @Transactional
    public ClassResponse update(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            UpdateClassRequest request, CurrentUser caller) {
        TrainingClass trainingClass = findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        if (!trainingClass.getName().equalsIgnoreCase(request.name())
                && trainingClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                        programPublicId, request.name())) {
            throw new StudentClassNameAlreadyUsedException();
        }
        trainingClass.setName(request.name());
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_CLASS_UPDATED, "Renamed Class to \"" + trainingClass.getName() + "\"");
        return TrainingClassMapper.toResponse(trainingClass, programPublicId);
    }

    /**
     * Loads a Program strictly by (organizationPublicId, programPublicId,
     * caller's tenant) â€” same "wrong scope looks like not-found" pattern as
     * {@code ProgramService.findOwned}.
     */
    private Program loadProgramOwned(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
        Program program = programRepository.findByPublicId(programPublicId)
                .orElseThrow(ProgramNotFoundException::new);
        Organization organization = program.getOrganization();
        if (!organization.getPublicId().equals(organizationPublicId)
                || !organization.getPublicId().equals(caller.tenantId())) {
            throw new ProgramNotFoundException();
        }
        return program;
    }

    /**
     * Loads a Class strictly by (organizationPublicId, programPublicId,
     * classPublicId, caller's tenant) â€” a class id that exists but belongs to a
     * different Program/Organization/tenant than requested is treated as
     * not-found, never silently served.
     */
    TrainingClass findOwned(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller) {
        TrainingClass trainingClass = trainingClassRepository.findByPublicId(classPublicId)
                .orElseThrow(StudentClassNotFoundException::new);
        if (!trainingClass.getProgram().getPublicId().equals(programPublicId)
                || !trainingClass.getOrganizationId().equals(organizationPublicId)
                || !trainingClass.getOrganizationId().equals(caller.tenantId())) {
            throw new StudentClassNotFoundException();
        }
        return trainingClass;
    }

}
