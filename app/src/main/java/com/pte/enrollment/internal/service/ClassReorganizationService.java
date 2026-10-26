package com.pte.enrollment.internal.service;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.enrollment.domain.ClassStudent;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.internal.exception.StudentClassNameAlreadyUsedException;
import com.pte.enrollment.internal.dto.request.MergeClassesRequest;
import com.pte.enrollment.internal.dto.request.SplitClassRequest;
import com.pte.enrollment.internal.dto.response.MergeClassesResponse;
import com.pte.enrollment.internal.dto.response.SplitClassResponse;
import com.pte.enrollment.internal.mapper.TrainingClassMapper;
import com.pte.enrollment.internal.repository.ClassStudentRepository;
import com.pte.enrollment.internal.repository.TrainingClassRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Merge and split of classes inside one program. Both only move {@link ClassStudent} rows;
 * archiving an emptied source class stays a separate action.
 */
@Service
public class ClassReorganizationService {

    private final ClassStudentRepository classStudentRepository;
    private final TrainingClassRepository trainingClassRepository;
    private final ClassService classService;
    private final AuditLogService auditLogService;

    public ClassReorganizationService(ClassStudentRepository classStudentRepository,
            TrainingClassRepository trainingClassRepository,
            ClassService classService,
            AuditLogService auditLogService) {
        this.classStudentRepository = classStudentRepository;
        this.trainingClassRepository = trainingClassRepository;
        this.classService = classService;
        this.auditLogService = auditLogService;
    }

    /**
     * Moves every student from each source Class into the target â€” target and
     * every source must belong to the same Organization/Program/tenant as the
     * request path (enforced via {@code ClassService.findOwned}, same as every other
     * Class-scoped method here). Does NOT archive the now-empty source
     * Class(es) â€” a deliberate choice (confirmed with the user during Phase 12
     * design): merge only moves membership rows, the Host archives a source
     * Class separately via the existing Phase 2/7 action if desired. A source
     * id equal to the target is silently skipped (no-op), not an error â€” the
     * Host may have included it accidentally. Writes one
     * {@code StudentTransferredClass} event per moved student (mirroring
     * {@code ClassStudentService.transfer}) plus a single {@code ClassesMerged} summary event.
     */
    @Transactional
    public MergeClassesResponse mergeClasses(UUID organizationPublicId, UUID programPublicId,
            UUID targetClassPublicId, MergeClassesRequest request, CurrentUser caller) {
        TrainingClass targetClass = classService.findOwned(organizationPublicId, programPublicId, targetClassPublicId, caller);

        List<UUID> movedStudentPublicIds = new ArrayList<>();
        for (UUID sourceClassPublicId : request.sourceClassPublicIds()) {
            if (sourceClassPublicId.equals(targetClassPublicId)) {
                continue;
            }
            classService.findOwned(organizationPublicId, programPublicId, sourceClassPublicId, caller);
            List<ClassStudent> memberships = classStudentRepository
                    .findByOrganizationIdAndTrainingClass_PublicId(caller.tenantId(), sourceClassPublicId);
            for (ClassStudent membership : memberships) {
                membership.setTrainingClass(targetClass);
                ClassStudent saved = classStudentRepository.save(membership);
                movedStudentPublicIds.add(saved.getStudentPublicId());
            }
        }
        if (!movedStudentPublicIds.isEmpty()) {
            auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, targetClassPublicId.toString(),
                    EnrollmentConstants.EVENT_CLASSES_MERGED,
                    "Merged " + request.sourceClassPublicIds().size() + " Class(es) into \"" + targetClass.getName()
                            + "\" (" + movedStudentPublicIds.size() + " student(s) moved)");
        }
        return new MergeClassesResponse(targetClassPublicId, request.sourceClassPublicIds(), movedStudentPublicIds);
    }

    /**
     * Creates a new {@code TrainingClass} under the SAME Program as the source
     * (never a different one â€” splitting shouldn't relocate a cohort across
     * the academic hierarchy), then moves the given subset of the source's
     * students into it. Any {@code studentPublicId} in the request that isn't
     * actually a member of the source Class is silently ignored (the
     * repository query only ever returns real matches) rather than erroring â€”
     * mirrors {@code ClassStudentService.bulkAssign}'s forgiving-subset style. Writes one
     * {@code StudentTransferredClass} event per moved student plus a single
     * {@code ClassSplit} summary event.
     */
    @Transactional
    public SplitClassResponse splitClass(UUID organizationPublicId, UUID programPublicId, UUID sourceClassPublicId,
            SplitClassRequest request, CurrentUser caller) {
        TrainingClass sourceClass = classService.findOwned(organizationPublicId, programPublicId, sourceClassPublicId, caller);
        if (trainingClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                programPublicId, request.newClassName())) {
            throw new StudentClassNameAlreadyUsedException();
        }

        TrainingClass newClass = new TrainingClass();
        newClass.setProgram(sourceClass.getProgram());
        newClass.setName(request.newClassName());
        TrainingClass savedNewClass = trainingClassRepository.save(newClass);
        List<ClassStudent> membershipsToMove = classStudentRepository
                .findByOrganizationIdAndTrainingClass_PublicIdAndStudentPublicIdIn(
                        caller.tenantId(), sourceClassPublicId, request.studentPublicIds());
        List<UUID> movedStudentPublicIds = new ArrayList<>();
        for (ClassStudent membership : membershipsToMove) {
            membership.setTrainingClass(savedNewClass);
            ClassStudent saved = classStudentRepository.save(membership);
            movedStudentPublicIds.add(saved.getStudentPublicId());
        }
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, savedNewClass.getPublicId().toString(),
                EnrollmentConstants.EVENT_CLASS_SPLIT,
                "Split Class into new Class \"" + savedNewClass.getName() + "\" (" + movedStudentPublicIds.size()
                        + " student(s) moved)");
        return new SplitClassResponse(TrainingClassMapper.toResponse(savedNewClass, programPublicId),
                movedStudentPublicIds);
    }

}
