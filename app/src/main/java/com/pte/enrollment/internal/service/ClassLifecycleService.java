package com.pte.enrollment.internal.service;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.domain.enums.ClassStatus;
import com.pte.enrollment.internal.exception.ClassHasActiveMembersException;
import com.pte.enrollment.internal.dto.response.ClassResponse;
import com.pte.enrollment.internal.mapper.TrainingClassMapper;
import com.pte.enrollment.internal.repository.ClassStudentRepository;
import com.pte.enrollment.internal.repository.TrainingClassRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Status transitions and archiving of a {@link TrainingClass}; deactivate and archive
 * require every student to be unassigned or transferred out first.
 */
@Service
public class ClassLifecycleService {

    private final TrainingClassRepository trainingClassRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ClassService classService;
    private final AuditLogService auditLogService;

    public ClassLifecycleService(TrainingClassRepository trainingClassRepository,
            ClassStudentRepository classStudentRepository,
            ClassService classService,
            AuditLogService auditLogService) {
        this.trainingClassRepository = trainingClassRepository;
        this.classStudentRepository = classStudentRepository;
        this.classService = classService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ClassResponse activate(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller) {
        return changeStatus(organizationPublicId, programPublicId, classPublicId, caller, ClassStatus.ACTIVE);
    }

    @Transactional
    public ClassResponse suspend(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller) {
        return changeStatus(organizationPublicId, programPublicId, classPublicId, caller, ClassStatus.SUSPENDED);
    }

    /**
     * Unlike {@code activate}/{@code suspend}, deactivating requires every student
     * to be unassigned/transferred out first.
     */
    @Transactional
    public ClassResponse deactivate(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller) {
        TrainingClass trainingClass = classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        if (trainingClass.getStatus() == ClassStatus.INACTIVE) {
            return TrainingClassMapper.toResponse(trainingClass, programPublicId);
        }
        if (classStudentRepository.existsByOrganizationIdAndTrainingClass_PublicId(caller.tenantId(), classPublicId)) {
            throw new ClassHasActiveMembersException();
        }
        trainingClass.setStatus(ClassStatus.INACTIVE);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_CLASS_STATUS_CHANGED,
                "Changed Class status to " + ClassStatus.INACTIVE.name());
        return TrainingClassMapper.toResponse(trainingClass, programPublicId);
    }

    /**
     * Visibility/lifecycle flag via the inherited {@code deleted} column, NOT a
     * hard delete â€” archived Classes stay fetchable by {@code ClassService.get} but drop out
     * of {@code ClassService.list}. Requires every student to be unassigned/transferred out
     * first, same guard as {@link #deactivate}.
     */
    @Transactional
    public ClassResponse archive(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller) {
        TrainingClass trainingClass = classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        if (trainingClass.isDeleted()) {
            return TrainingClassMapper.toResponse(trainingClass, programPublicId);
        }
        if (classStudentRepository.existsByOrganizationIdAndTrainingClass_PublicId(caller.tenantId(), classPublicId)) {
            throw new ClassHasActiveMembersException();
        }
        trainingClass.setDeleted(true);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_CLASS_ARCHIVED, "Archived Class \"" + trainingClass.getName() + "\"");
        return TrainingClassMapper.toResponse(trainingClass, programPublicId);
    }


    private ClassResponse changeStatus(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller, ClassStatus target) {
        TrainingClass trainingClass = classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        if (trainingClass.getStatus() == target) {
            return TrainingClassMapper.toResponse(trainingClass, programPublicId);
        }
        trainingClass.setStatus(target);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_CLASS_STATUS_CHANGED, "Changed Class status to " + target.name());
        return TrainingClassMapper.toResponse(trainingClass, programPublicId);
    }

}
