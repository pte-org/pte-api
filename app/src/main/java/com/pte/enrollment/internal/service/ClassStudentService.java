package com.pte.enrollment.internal.service;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.enrollment.domain.ClassStudent;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.internal.exception.ClassMembershipNotFoundException;
import com.pte.enrollment.internal.exception.StudentAlreadyInClassException;
import com.pte.enrollment.internal.exception.StudentClassNotFoundException;
import com.pte.enrollment.internal.exception.StudentNotFoundException;
import com.pte.enrollment.internal.dto.request.AssignStudentRequest;
import com.pte.enrollment.internal.dto.request.BulkAssignStudentsRequest;
import com.pte.enrollment.internal.dto.request.TransferStudentRequest;
import com.pte.enrollment.internal.dto.response.BulkAssignStudentsResponse;
import com.pte.enrollment.internal.dto.response.ClassMembershipResponse;
import com.pte.enrollment.internal.mapper.ClassStudentMapper;
import com.pte.enrollment.internal.repository.ClassStudentRepository;
import com.pte.enrollment.internal.repository.TrainingClassRepository;
import com.pte.identity.IdentityService;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@link ClassStudent} CRUD (assign/bulkAssign/unassign/transfer) and the tenant-wide
 * roster listing. Every method resolves tenant scope from {@code caller.tenantId()}
 * (JWT), never a path param.
 */
@Service
public class ClassStudentService {

    private final ClassStudentRepository classStudentRepository;
    private final TrainingClassRepository trainingClassRepository;
    private final ClassService classService;
    private final IdentityService identityService;
    private final AuditLogService auditLogService;

    public ClassStudentService(ClassStudentRepository classStudentRepository,
            TrainingClassRepository trainingClassRepository,
            ClassService classService,
            IdentityService identityService,
            AuditLogService auditLogService) {
        this.classStudentRepository = classStudentRepository;
        this.trainingClassRepository = trainingClassRepository;
        this.classService = classService;
        this.identityService = identityService;
        this.auditLogService = auditLogService;
    }

    /**
     * Rejected if the student already has a membership row anywhere â€” the 1-N
     * student/Class rule is a DB unique constraint on {@code studentPublicId}
     * alone, transfer is the explicit separate operation for moving a student.
     */
    @Transactional
    public ClassMembershipResponse assign(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            AssignStudentRequest request, CurrentUser caller) {
        TrainingClass trainingClass = classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        assertStudentBelongsToTenant(request.studentPublicId(), caller.tenantId());
        classStudentRepository.findByOrganizationIdAndStudentPublicId(caller.tenantId(), request.studentPublicId())
                .ifPresent(existingMembership -> {
                    UUID existingClassPublicId = existingMembership.getTrainingClass().getPublicId();
                    throw new StudentAlreadyInClassException(existingClassPublicId);
                });

        ClassStudent membership = new ClassStudent();
        membership.setTrainingClass(trainingClass);
        membership.setStudentPublicId(request.studentPublicId());
        membership.setOrganizationId(caller.tenantId());

        ClassStudent saved;
        try {
            saved = classStudentRepository.save(membership);
        } catch (DataIntegrityViolationException ex) {
            throw new StudentAlreadyInClassException();
        }
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_STUDENT_ASSIGNED_TO_CLASS,
                "Assigned student to Class \"" + trainingClass.getName() + "\"");
        return ClassStudentMapper.toResponse(saved);
    }

    /**
     * Bulk-assigns students not already in any Class (already-assigned ids are
     * reported, not errors); intra-request duplicate ids are also deduped.
     * Mirrors {@code scheduling.EnrollmentService.bulkEnroll}'s dedupe/
     * already-assigned-reporting logic exactly, including the {@code saveAll}
     * catch for the rare true concurrent-double-assign race.
     */
    @Transactional
    public BulkAssignStudentsResponse bulkAssign(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            BulkAssignStudentsRequest request, CurrentUser caller) {
        TrainingClass trainingClass = classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        request.studentPublicIds()
                .forEach(studentPublicId -> assertStudentBelongsToTenant(studentPublicId, caller.tenantId()));

        List<UUID> existing = classStudentRepository
                .findByOrganizationIdAndStudentPublicIdIn(caller.tenantId(), request.studentPublicIds())
                .stream().map(ClassStudent::getStudentPublicId).toList();
        Set<UUID> alreadyInClass = new HashSet<>(existing);

        List<ClassStudent> toCreate = new ArrayList<>();
        Set<UUID> seenInBatch = new HashSet<>();
        for (UUID studentPublicId : request.studentPublicIds()) {
            if (alreadyInClass.contains(studentPublicId) || !seenInBatch.add(studentPublicId)) {
                continue;
            }
            ClassStudent membership = new ClassStudent();
            membership.setTrainingClass(trainingClass);
            membership.setStudentPublicId(studentPublicId);
            membership.setOrganizationId(caller.tenantId());
            toCreate.add(membership);
        }

        List<ClassStudent> saved;
        try {
            saved = classStudentRepository.saveAll(toCreate);
        } catch (DataIntegrityViolationException ex) {
            throw new StudentAlreadyInClassException();
        }

        List<UUID> assigned = saved.stream().map(ClassStudent::getStudentPublicId).toList();
        if (!assigned.isEmpty()) {
            auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                    EnrollmentConstants.EVENT_STUDENT_ASSIGNED_TO_CLASS,
                    "Bulk-assigned " + assigned.size() + " student(s) to Class \"" + trainingClass.getName()
                            + "\" (" + alreadyInClass.size() + " already assigned)");
        }
        return new BulkAssignStudentsResponse(assigned, List.copyOf(alreadyInClass));
    }

    @Transactional
    public void unassign(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            UUID membershipPublicId, CurrentUser caller) {
        TrainingClass trainingClass = classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        ClassStudent membership = loadMembershipOwned(classPublicId, membershipPublicId, caller.tenantId());
        classStudentRepository.delete(membership);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_STUDENT_UNASSIGNED_FROM_CLASS,
                "Unassigned student from Class \"" + trainingClass.getName() + "\"");
    }

    /**
     * Updates the existing membership row's {@code trainingClass} FK in place â€”
     * never delete+recreate â€” which is exactly what makes "preserving exam
     * history" trivially true: {@code scheduling.Enrollment} has no reference to
     * {@code ClassStudent}/{@code TrainingClass} at all (Decision 1), so a
     * transfer never touches {@code scheduling} data by construction. The
     * target Class only needs to belong to the same tenant â€” it may be under a
     * different Program than the source.
     */
    @Transactional
    public ClassMembershipResponse transfer(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            UUID membershipPublicId, TransferStudentRequest request, CurrentUser caller) {
        classService.findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        ClassStudent membership = loadMembershipOwned(classPublicId, membershipPublicId, caller.tenantId());
        TrainingClass targetClass = loadClassForTenant(request.targetClassPublicId(), caller.tenantId());

        membership.setTrainingClass(targetClass);
        ClassStudent saved = classStudentRepository.save(membership);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, targetClass.getPublicId().toString(),
                EnrollmentConstants.EVENT_STUDENT_TRANSFERRED_CLASS,
                "Transferred student to Class \"" + targetClass.getName() + "\"");
        return ClassStudentMapper.toResponse(saved);
    }

    /**
     * Management roster, optionally filtered by program. Assignment visibility
     * is independent of the active/date eligibility rules used by exam audiences.
     */
    @Transactional(readOnly = true)
    public List<ClassMembershipResponse> listMemberships(UUID programPublicId, CurrentUser caller) {
        List<ClassStudent> memberships = classStudentRepository
                .findManagementMemberships(caller.tenantId(), programPublicId);
        return memberships.stream().map(ClassStudentMapper::toResponse).toList();
    }

    /**
     * Loads a Class by (classPublicId, tenant) only â€” used to validate a transfer
     * target, which may be under a different Program.
     */
    private TrainingClass loadClassForTenant(UUID classPublicId, UUID tenantId) {
        TrainingClass trainingClass = trainingClassRepository.findByPublicId(classPublicId)
                .orElseThrow(StudentClassNotFoundException::new);
        if (!tenantId.equals(trainingClass.getOrganizationId())) {
            throw new StudentClassNotFoundException();
        }
        return trainingClass;
    }

    private ClassStudent loadMembershipOwned(UUID classPublicId, UUID membershipPublicId, UUID tenantId) {
        ClassStudent membership = classStudentRepository.findByPublicIdAndOrganizationId(membershipPublicId, tenantId)
                .orElseThrow(ClassMembershipNotFoundException::new);
        if (!membership.getTrainingClass().getPublicId().equals(classPublicId)) {
            throw new ClassMembershipNotFoundException();
        }
        return membership;
    }

    private void assertStudentBelongsToTenant(UUID studentPublicId, UUID tenantId) {
        if (!identityService.isMemberOf(studentPublicId, tenantId)) {
            throw new StudentNotFoundException();
        }
        identityService.requireActiveStudentInTenant(studentPublicId, tenantId);
    }
}
