package com.pte.enrollment.internal.service;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.enrollment.domain.ClassMembership;
import com.pte.tenancy.domain.Organization;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.StudentClass;
import com.pte.enrollment.domain.enums.ClassStatus;
import com.pte.enrollment.internal.exception.ClassHasActiveMembersException;
import com.pte.enrollment.internal.exception.ClassMembershipNotFoundException;
import com.pte.enrollment.internal.exception.ProgramNotFoundException;
import com.pte.enrollment.internal.exception.StudentAlreadyInClassException;
import com.pte.enrollment.internal.exception.StudentClassNameAlreadyUsedException;
import com.pte.enrollment.internal.exception.StudentClassNotFoundException;
import com.pte.enrollment.internal.exception.StudentNotFoundException;
import com.pte.enrollment.internal.dto.request.AssignStudentRequest;
import com.pte.enrollment.internal.dto.request.BulkAssignStudentsRequest;
import com.pte.enrollment.internal.dto.request.CreateClassRequest;
import com.pte.enrollment.internal.dto.request.MergeClassesRequest;
import com.pte.enrollment.internal.dto.request.SplitClassRequest;
import com.pte.enrollment.internal.dto.request.TransferStudentRequest;
import com.pte.enrollment.internal.dto.request.UpdateClassRequest;
import com.pte.enrollment.internal.dto.response.BulkAssignStudentsResponse;
import com.pte.enrollment.internal.dto.response.ClassMembershipResponse;
import com.pte.enrollment.internal.dto.response.ClassResponse;
import com.pte.enrollment.internal.dto.response.MergeClassesResponse;
import com.pte.enrollment.internal.dto.response.SplitClassResponse;
import com.pte.enrollment.internal.mapper.ClassMembershipMapper;
import com.pte.enrollment.internal.mapper.StudentClassMapper;
import com.pte.enrollment.internal.repository.ClassMembershipRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.StudentClassRepository;
import com.pte.identity.internal.service.IdentityService;
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
 * Owns both {@link StudentClass} CRUD/lifecycle AND {@link ClassMembership}
 * CRUD (assign/bulkAssign/unassign/transfer) â€” same "one service, two
 * join-CRUDs" shape as {@code scheduling.EnrollmentService} (deliberate, not
 * scope creep). Every method resolves tenant scope from {@code
 * caller.tenantId()} (JWT), never a path param â€” same pattern as {@code
 * ProgramService}.
 */
@Service
public class ClassService {

    private final StudentClassRepository studentClassRepository;
    private final ClassMembershipRepository classMembershipRepository;
    private final ProgramRepository programRepository;
    private final IdentityService identityService;
    private final AuditLogService auditLogService;

    public ClassService(StudentClassRepository studentClassRepository,
            ClassMembershipRepository classMembershipRepository, ProgramRepository programRepository,
            IdentityService identityService, AuditLogService auditLogService) {
        this.studentClassRepository = studentClassRepository;
        this.classMembershipRepository = classMembershipRepository;
        this.programRepository = programRepository;
        this.auditLogService = auditLogService;
        this.identityService = identityService;
    }

    @Transactional
    public ClassResponse create(UUID organizationPublicId, UUID programPublicId, CreateClassRequest request,
            CurrentUser caller) {
        Program program = loadProgramOwned(organizationPublicId, programPublicId, caller);
        if (studentClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                programPublicId, request.name())) {
            throw new StudentClassNameAlreadyUsedException();
        }

        StudentClass studentClass = new StudentClass();
        studentClass.setProgram(program);
        studentClass.setName(request.name());
        StudentClass saved = studentClassRepository.save(studentClass);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, saved.getPublicId().toString(),
                EnrollmentConstants.EVENT_CLASS_CREATED, "Created Class \"" + saved.getName() + "\"");
        return StudentClassMapper.toResponse(saved, programPublicId);
    }

    @Transactional(readOnly = true)
    public List<ClassResponse> list(UUID organizationPublicId, UUID programPublicId, CurrentUser caller) {
        loadProgramOwned(organizationPublicId, programPublicId, caller);
        return studentClassRepository.findByProgram_PublicIdAndDeletedFalseOrderByCreatedAtAsc(programPublicId)
                .stream()
                .map(studentClass -> StudentClassMapper.toResponse(studentClass, programPublicId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassResponse get(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller) {
        StudentClass studentClass = findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        return StudentClassMapper.toResponse(studentClass, programPublicId);
    }

    @Transactional
    public ClassResponse update(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            UpdateClassRequest request, CurrentUser caller) {
        StudentClass studentClass = findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        if (!studentClass.getName().equalsIgnoreCase(request.name())
                && studentClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                        programPublicId, request.name())) {
            throw new StudentClassNameAlreadyUsedException();
        }
        studentClass.setName(request.name());
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_CLASS_UPDATED, "Renamed Class to \"" + studentClass.getName() + "\"");
        return StudentClassMapper.toResponse(studentClass, programPublicId);
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
        StudentClass studentClass = findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        if (studentClass.getStatus() == ClassStatus.INACTIVE) {
            return StudentClassMapper.toResponse(studentClass, programPublicId);
        }
        if (classMembershipRepository.existsByTenantIdAndStudentClass_PublicId(caller.tenantId(), classPublicId)) {
            throw new ClassHasActiveMembersException();
        }
        studentClass.setStatus(ClassStatus.INACTIVE);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_CLASS_STATUS_CHANGED,
                "Changed Class status to " + ClassStatus.INACTIVE.name());
        return StudentClassMapper.toResponse(studentClass, programPublicId);
    }

    /**
     * Visibility/lifecycle flag via the inherited {@code deleted} column, NOT a
     * hard delete â€” archived Classes stay fetchable by {@link #get} but drop out
     * of {@link #list}. Requires every student to be unassigned/transferred out
     * first, same guard as {@link #deactivate}.
     */
    @Transactional
    public ClassResponse archive(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller) {
        StudentClass studentClass = findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        if (studentClass.isDeleted()) {
            return StudentClassMapper.toResponse(studentClass, programPublicId);
        }
        if (classMembershipRepository.existsByTenantIdAndStudentClass_PublicId(caller.tenantId(), classPublicId)) {
            throw new ClassHasActiveMembersException();
        }
        studentClass.setDeleted(true);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_CLASS_ARCHIVED, "Archived Class \"" + studentClass.getName() + "\"");
        return StudentClassMapper.toResponse(studentClass, programPublicId);
    }

    /**
     * Rejected if the student already has a membership row anywhere â€” the 1-N
     * student/Class rule is a DB unique constraint on {@code studentPublicId}
     * alone, transfer is the explicit separate operation for moving a student.
     */
    @Transactional
    public ClassMembershipResponse assign(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            AssignStudentRequest request, CurrentUser caller) {
        StudentClass studentClass = findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        assertStudentBelongsToTenant(request.studentPublicId(), caller.tenantId());
        if (classMembershipRepository.existsByStudentPublicIdAndTenantId(
                request.studentPublicId(), caller.tenantId())) {
            throw new StudentAlreadyInClassException();
        }

        ClassMembership membership = new ClassMembership();
        membership.setStudentClass(studentClass);
        membership.setStudentPublicId(request.studentPublicId());
        membership.setTenantId(caller.tenantId());

        ClassMembership saved;
        try {
            saved = classMembershipRepository.save(membership);
        } catch (DataIntegrityViolationException ex) {
            throw new StudentAlreadyInClassException();
        }
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_STUDENT_ASSIGNED_TO_CLASS,
                "Assigned student " + saved.getStudentPublicId() + " to Class \"" + studentClass.getName() + "\"");
        return ClassMembershipMapper.toResponse(saved);
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
        StudentClass studentClass = findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        request.studentPublicIds()
                .forEach(studentPublicId -> assertStudentBelongsToTenant(studentPublicId, caller.tenantId()));

        List<UUID> existing = classMembershipRepository
                .findByTenantIdAndStudentPublicIdIn(caller.tenantId(), request.studentPublicIds())
                .stream().map(ClassMembership::getStudentPublicId).toList();
        Set<UUID> alreadyInClass = new HashSet<>(existing);

        List<ClassMembership> toCreate = new ArrayList<>();
        Set<UUID> seenInBatch = new HashSet<>();
        for (UUID studentPublicId : request.studentPublicIds()) {
            if (alreadyInClass.contains(studentPublicId) || !seenInBatch.add(studentPublicId)) {
                continue;
            }
            ClassMembership membership = new ClassMembership();
            membership.setStudentClass(studentClass);
            membership.setStudentPublicId(studentPublicId);
            membership.setTenantId(caller.tenantId());
            toCreate.add(membership);
        }

        List<ClassMembership> saved;
        try {
            saved = classMembershipRepository.saveAll(toCreate);
        } catch (DataIntegrityViolationException ex) {
            throw new StudentAlreadyInClassException();
        }

        List<UUID> assigned = saved.stream().map(ClassMembership::getStudentPublicId).toList();
        if (!assigned.isEmpty()) {
            auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                    EnrollmentConstants.EVENT_STUDENT_ASSIGNED_TO_CLASS,
                    "Bulk-assigned " + assigned.size() + " student(s) to Class \"" + studentClass.getName()
                            + "\" (" + alreadyInClass.size() + " already assigned)");
        }
        return new BulkAssignStudentsResponse(assigned, List.copyOf(alreadyInClass));
    }

    @Transactional
    public void unassign(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            UUID membershipPublicId, CurrentUser caller) {
        findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        ClassMembership membership = loadMembershipOwned(classPublicId, membershipPublicId, caller.tenantId());
        classMembershipRepository.delete(membership);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_STUDENT_UNASSIGNED_FROM_CLASS,
                "Unassigned student " + membership.getStudentPublicId() + " from Class");
    }

    /**
     * Updates the existing membership row's {@code studentClass} FK in place â€”
     * never delete+recreate â€” which is exactly what makes "preserving exam
     * history" trivially true: {@code scheduling.Enrollment} has no reference to
     * {@code ClassMembership}/{@code StudentClass} at all (Decision 1), so a
     * transfer never touches {@code scheduling} data by construction. The
     * target Class only needs to belong to the same tenant â€” it may be under a
     * different Program than the source.
     */
    @Transactional
    public ClassMembershipResponse transfer(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            UUID membershipPublicId, TransferStudentRequest request, CurrentUser caller) {
        findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        ClassMembership membership = loadMembershipOwned(classPublicId, membershipPublicId, caller.tenantId());
        StudentClass targetClass = loadClassForTenant(request.targetClassPublicId(), caller.tenantId());

        membership.setStudentClass(targetClass);
        ClassMembership saved = classMembershipRepository.save(membership);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, targetClass.getPublicId().toString(),
                EnrollmentConstants.EVENT_STUDENT_TRANSFERRED_CLASS,
                "Transferred student " + saved.getStudentPublicId() + " to Class \"" + targetClass.getName() + "\"");
        return ClassMembershipMapper.toResponse(saved);
    }

    /**
     * Moves every student from each source Class into the target â€” target and
     * every source must belong to the same Organization/Program/tenant as the
     * request path (enforced via {@link #findOwned}, same as every other
     * Class-scoped method here). Does NOT archive the now-empty source
     * Class(es) â€” a deliberate choice (confirmed with the user during Phase 12
     * design): merge only moves membership rows, the Host archives a source
     * Class separately via the existing Phase 2/7 action if desired. A source
     * id equal to the target is silently skipped (no-op), not an error â€” the
     * Host may have included it accidentally. Writes one
     * {@code StudentTransferredClass} event per moved student (mirroring
     * {@link #transfer}) plus a single {@code ClassesMerged} summary event.
     */
    @Transactional
    public MergeClassesResponse mergeClasses(UUID organizationPublicId, UUID programPublicId,
            UUID targetClassPublicId, MergeClassesRequest request, CurrentUser caller) {
        StudentClass targetClass = findOwned(organizationPublicId, programPublicId, targetClassPublicId, caller);

        List<UUID> movedStudentPublicIds = new ArrayList<>();
        for (UUID sourceClassPublicId : request.sourceClassPublicIds()) {
            if (sourceClassPublicId.equals(targetClassPublicId)) {
                continue;
            }
            findOwned(organizationPublicId, programPublicId, sourceClassPublicId, caller);
            List<ClassMembership> memberships = classMembershipRepository
                    .findByTenantIdAndStudentClass_PublicId(caller.tenantId(), sourceClassPublicId);
            for (ClassMembership membership : memberships) {
                membership.setStudentClass(targetClass);
                ClassMembership saved = classMembershipRepository.save(membership);
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
     * Creates a new {@code StudentClass} under the SAME Program as the source
     * (never a different one â€” splitting shouldn't relocate a cohort across
     * the academic hierarchy), then moves the given subset of the source's
     * students into it. Any {@code studentPublicId} in the request that isn't
     * actually a member of the source Class is silently ignored (the
     * repository query only ever returns real matches) rather than erroring â€”
     * mirrors {@link #bulkAssign}'s forgiving-subset style. Writes one
     * {@code StudentTransferredClass} event per moved student plus a single
     * {@code ClassSplit} summary event.
     */
    @Transactional
    public SplitClassResponse splitClass(UUID organizationPublicId, UUID programPublicId, UUID sourceClassPublicId,
            SplitClassRequest request, CurrentUser caller) {
        StudentClass sourceClass = findOwned(organizationPublicId, programPublicId, sourceClassPublicId, caller);
        if (studentClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                programPublicId, request.newClassName())) {
            throw new StudentClassNameAlreadyUsedException();
        }

        StudentClass newClass = new StudentClass();
        newClass.setProgram(sourceClass.getProgram());
        newClass.setName(request.newClassName());
        StudentClass savedNewClass = studentClassRepository.save(newClass);
        List<ClassMembership> membershipsToMove = classMembershipRepository
                .findByTenantIdAndStudentClass_PublicIdAndStudentPublicIdIn(
                        caller.tenantId(), sourceClassPublicId, request.studentPublicIds());
        List<UUID> movedStudentPublicIds = new ArrayList<>();
        for (ClassMembership membership : membershipsToMove) {
            membership.setStudentClass(savedNewClass);
            ClassMembership saved = classMembershipRepository.save(membership);
            movedStudentPublicIds.add(saved.getStudentPublicId());
        }
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, savedNewClass.getPublicId().toString(),
                EnrollmentConstants.EVENT_CLASS_SPLIT,
                "Split Class into new Class \"" + savedNewClass.getName() + "\" (" + movedStudentPublicIds.size()
                        + " student(s) moved)");
        return new SplitClassResponse(StudentClassMapper.toResponse(savedNewClass, programPublicId),
                movedStudentPublicIds);
    }

    /**
     * Tenant-wide roster, optionally filtered by Program â€” the shared data
     * source Phase 7/10/13 reuse. {@code caller.tenantId()} is always forwarded
     * into the repository query, never checked separately from the program
     * filter (Design Constraint â€” a decoupled check here would be a
     * cross-tenant leak, not just an N+1 problem).
     */
    @Transactional(readOnly = true)
    public List<ClassMembershipResponse> listMemberships(UUID programPublicId, CurrentUser caller) {
        List<ClassMembership> memberships = programPublicId != null
                ? classMembershipRepository
                        .findByStudentClass_Program_Organization_Tenant_PublicIdAndStudentClass_Program_PublicId(
                                caller.tenantId(), programPublicId)
                : classMembershipRepository.findByStudentClass_Program_Organization_Tenant_PublicId(caller.tenantId());
        return memberships.stream().map(ClassMembershipMapper::toResponse).toList();
    }

    private ClassResponse changeStatus(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller, ClassStatus target) {
        StudentClass studentClass = findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        if (studentClass.getStatus() == target) {
            return StudentClassMapper.toResponse(studentClass, programPublicId);
        }
        studentClass.setStatus(target);
        auditLogService.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_CLASS_STATUS_CHANGED, "Changed Class status to " + target.name());
        return StudentClassMapper.toResponse(studentClass, programPublicId);
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
                || !organization.getTenant().getPublicId().equals(caller.tenantId())) {
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
    StudentClass findOwned(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller) {
        StudentClass studentClass = studentClassRepository.findByPublicId(classPublicId)
                .orElseThrow(StudentClassNotFoundException::new);
        Program program = studentClass.getProgram();
        Organization organization = program.getOrganization();
        if (!program.getPublicId().equals(programPublicId)
                || !organization.getPublicId().equals(organizationPublicId)
                || !organization.getTenant().getPublicId().equals(caller.tenantId())) {
            throw new StudentClassNotFoundException();
        }
        return studentClass;
    }

    /**
     * Loads a Class by (classPublicId, tenant) only â€” used to validate a transfer
     * target, which may be under a different Program.
     */
    private StudentClass loadClassForTenant(UUID classPublicId, UUID tenantId) {
        StudentClass studentClass = studentClassRepository.findByPublicId(classPublicId)
                .orElseThrow(StudentClassNotFoundException::new);
        if (!studentClass.getProgram().getOrganization().getTenant().getPublicId().equals(tenantId)) {
            throw new StudentClassNotFoundException();
        }
        return studentClass;
    }

    private ClassMembership loadMembershipOwned(UUID classPublicId, UUID membershipPublicId, UUID tenantId) {
        ClassMembership membership = classMembershipRepository.findByPublicIdAndTenantId(membershipPublicId, tenantId)
                .orElseThrow(ClassMembershipNotFoundException::new);
        if (!membership.getStudentClass().getPublicId().equals(classPublicId)) {
            throw new ClassMembershipNotFoundException();
        }
        return membership;
    }

    private void assertStudentBelongsToTenant(UUID studentPublicId, UUID tenantId) {
        if (!identityService.getTenantOf(studentPublicId).filter(tenantId::equals).isPresent()) {
            throw new StudentNotFoundException();
        }
    }
}
