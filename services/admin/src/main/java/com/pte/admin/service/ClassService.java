package com.pte.admin.service;

import com.pte.admin.constant.AdminConstants;
import com.pte.admin.domain.ClassMembership;
import com.pte.admin.domain.Organization;
import com.pte.admin.domain.Program;
import com.pte.admin.domain.StudentClass;
import com.pte.admin.domain.enums.ClassStatus;
import com.pte.admin.domain.event.ClassArchivedEvent;
import com.pte.admin.domain.event.ClassCreatedEvent;
import com.pte.admin.domain.event.ClassSplitEvent;
import com.pte.admin.domain.event.ClassStatusChangedEvent;
import com.pte.admin.domain.event.ClassUpdatedEvent;
import com.pte.admin.domain.event.ClassesMergedEvent;
import com.pte.admin.domain.event.StudentAssignedToClassEvent;
import com.pte.admin.domain.event.StudentTransferredClassEvent;
import com.pte.admin.domain.event.StudentUnassignedFromClassEvent;
import com.pte.admin.domain.exception.ClassHasActiveMembersException;
import com.pte.admin.domain.exception.ClassMembershipNotFoundException;
import com.pte.admin.domain.exception.ProgramNotFoundException;
import com.pte.admin.domain.exception.StudentAlreadyInClassException;
import com.pte.admin.domain.exception.StudentClassNameAlreadyUsedException;
import com.pte.admin.domain.exception.StudentClassNotFoundException;
import com.pte.admin.dto.request.AssignStudentRequest;
import com.pte.admin.dto.request.BulkAssignStudentsRequest;
import com.pte.admin.dto.request.CreateClassRequest;
import com.pte.admin.dto.request.MergeClassesRequest;
import com.pte.admin.dto.request.SplitClassRequest;
import com.pte.admin.dto.request.TransferStudentRequest;
import com.pte.admin.dto.request.UpdateClassRequest;
import com.pte.admin.dto.response.BulkAssignStudentsResponse;
import com.pte.admin.dto.response.ClassMembershipResponse;
import com.pte.admin.dto.response.ClassResponse;
import com.pte.admin.dto.response.MergeClassesResponse;
import com.pte.admin.dto.response.SplitClassResponse;
import com.pte.admin.mapper.ClassMembershipMapper;
import com.pte.admin.mapper.StudentClassMapper;
import com.pte.admin.messaging.outbox.OutboxWriter;
import com.pte.admin.repository.ClassMembershipRepository;
import com.pte.admin.repository.ProgramRepository;
import com.pte.admin.repository.StudentClassRepository;
import com.pte.common.security.CurrentUser;
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
 * CRUD (assign/bulkAssign/unassign/transfer) — same "one service, two
 * join-CRUDs" shape as {@code scheduling.EnrollmentService} (deliberate, not
 * scope creep). Every method resolves tenant scope from {@code
 * caller.tenantId()} (JWT), never a path param — same pattern as {@code
 * ProgramService}.
 */
@Service
public class ClassService {

    private final StudentClassRepository studentClassRepository;
    private final ClassMembershipRepository classMembershipRepository;
    private final ProgramRepository programRepository;
    private final OutboxWriter outboxWriter;
    private final AuditLogService auditLogService;

    public ClassService(StudentClassRepository studentClassRepository,
            ClassMembershipRepository classMembershipRepository, ProgramRepository programRepository,
            OutboxWriter outboxWriter, AuditLogService auditLogService) {
        this.studentClassRepository = studentClassRepository;
        this.classMembershipRepository = classMembershipRepository;
        this.programRepository = programRepository;
        this.outboxWriter = outboxWriter;
        this.auditLogService = auditLogService;
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

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, saved.getPublicId().toString(),
                AdminConstants.EVENT_CLASS_CREATED,
                new ClassCreatedEvent(saved.getPublicId(), programPublicId, caller.tenantId(), saved.getName()),
                caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_CLASS, saved.getPublicId().toString(),
                AdminConstants.EVENT_CLASS_CREATED, "Created Class \"" + saved.getName() + "\"");
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

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_CLASS_UPDATED,
                new ClassUpdatedEvent(classPublicId, programPublicId, caller.tenantId()), caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_CLASS_UPDATED, "Renamed Class to \"" + studentClass.getName() + "\"");
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

    /** Unlike {@code activate}/{@code suspend}, deactivating requires every student to be unassigned/transferred out first. */
    @Transactional
    public ClassResponse deactivate(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            CurrentUser caller) {
        StudentClass studentClass = findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        if (studentClass.getStatus() == ClassStatus.INACTIVE) {
            return StudentClassMapper.toResponse(studentClass, programPublicId);
        }
        if (classMembershipRepository.existsByStudentClass_PublicId(classPublicId)) {
            throw new ClassHasActiveMembersException();
        }
        studentClass.setStatus(ClassStatus.INACTIVE);

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_CLASS_STATUS_CHANGED,
                new ClassStatusChangedEvent(classPublicId, programPublicId, caller.tenantId(), ClassStatus.INACTIVE.name()),
                caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_CLASS_STATUS_CHANGED, "Changed Class status to " + ClassStatus.INACTIVE.name());
        return StudentClassMapper.toResponse(studentClass, programPublicId);
    }

    /**
     * Visibility/lifecycle flag via the inherited {@code deleted} column, NOT a
     * hard delete — archived Classes stay fetchable by {@link #get} but drop out
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
        if (classMembershipRepository.existsByStudentClass_PublicId(classPublicId)) {
            throw new ClassHasActiveMembersException();
        }
        studentClass.setDeleted(true);

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_CLASS_ARCHIVED,
                new ClassArchivedEvent(classPublicId, programPublicId, caller.tenantId()), caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_CLASS_ARCHIVED, "Archived Class \"" + studentClass.getName() + "\"");
        return StudentClassMapper.toResponse(studentClass, programPublicId);
    }

    /**
     * Rejected if the student already has a membership row anywhere — the 1-N
     * student/Class rule is a DB unique constraint on {@code studentPublicId}
     * alone, transfer is the explicit separate operation for moving a student.
     */
    @Transactional
    public ClassMembershipResponse assign(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            AssignStudentRequest request, CurrentUser caller) {
        StudentClass studentClass = findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        if (classMembershipRepository.existsByStudentPublicId(request.studentPublicId())) {
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

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_STUDENT_ASSIGNED_TO_CLASS,
                new StudentAssignedToClassEvent(saved.getPublicId(), classPublicId, saved.getStudentPublicId(),
                        caller.tenantId()),
                caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_STUDENT_ASSIGNED_TO_CLASS,
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

        List<UUID> existing = classMembershipRepository.findByStudentPublicIdIn(request.studentPublicIds())
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

        for (ClassMembership membership : saved) {
            outboxWriter.write(AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                    AdminConstants.EVENT_STUDENT_ASSIGNED_TO_CLASS,
                    new StudentAssignedToClassEvent(membership.getPublicId(), classPublicId,
                            membership.getStudentPublicId(), caller.tenantId()),
                    caller.tenantId());
        }

        List<UUID> assigned = saved.stream().map(ClassMembership::getStudentPublicId).toList();
        if (!assigned.isEmpty()) {
            auditLogService.record(caller, AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                    AdminConstants.EVENT_STUDENT_ASSIGNED_TO_CLASS,
                    "Bulk-assigned " + assigned.size() + " student(s) to Class \"" + studentClass.getName()
                            + "\" (" + alreadyInClass.size() + " already assigned)");
        }
        return new BulkAssignStudentsResponse(assigned, List.copyOf(alreadyInClass));
    }

    @Transactional
    public void unassign(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            UUID membershipPublicId, CurrentUser caller) {
        findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        ClassMembership membership = loadMembershipOwned(classPublicId, membershipPublicId);
        classMembershipRepository.delete(membership);

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_STUDENT_UNASSIGNED_FROM_CLASS,
                new StudentUnassignedFromClassEvent(membershipPublicId, classPublicId,
                        membership.getStudentPublicId(), caller.tenantId()),
                caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_STUDENT_UNASSIGNED_FROM_CLASS,
                "Unassigned student " + membership.getStudentPublicId() + " from Class");
    }

    /**
     * Updates the existing membership row's {@code studentClass} FK in place —
     * never delete+recreate — which is exactly what makes "preserving exam
     * history" trivially true: {@code scheduling.Enrollment} has no reference to
     * {@code ClassMembership}/{@code StudentClass} at all (Decision 1), so a
     * transfer never touches {@code scheduling} data by construction. The
     * target Class only needs to belong to the same tenant — it may be under a
     * different Program than the source.
     */
    @Transactional
    public ClassMembershipResponse transfer(UUID organizationPublicId, UUID programPublicId, UUID classPublicId,
            UUID membershipPublicId, TransferStudentRequest request, CurrentUser caller) {
        findOwned(organizationPublicId, programPublicId, classPublicId, caller);
        ClassMembership membership = loadMembershipOwned(classPublicId, membershipPublicId);
        StudentClass targetClass = loadClassForTenant(request.targetClassPublicId(), caller.tenantId());

        membership.setStudentClass(targetClass);
        ClassMembership saved = classMembershipRepository.save(membership);

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, targetClass.getPublicId().toString(),
                AdminConstants.EVENT_STUDENT_TRANSFERRED_CLASS,
                new StudentTransferredClassEvent(saved.getPublicId(), classPublicId, targetClass.getPublicId(),
                        saved.getStudentPublicId(), caller.tenantId()),
                caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_CLASS, targetClass.getPublicId().toString(),
                AdminConstants.EVENT_STUDENT_TRANSFERRED_CLASS,
                "Transferred student " + saved.getStudentPublicId() + " to Class \"" + targetClass.getName() + "\"");
        return ClassMembershipMapper.toResponse(saved);
    }

    /**
     * Moves every student from each source Class into the target — target and
     * every source must belong to the same Organization/Program/tenant as the
     * request path (enforced via {@link #findOwned}, same as every other
     * Class-scoped method here). Does NOT archive the now-empty source
     * Class(es) — a deliberate choice (confirmed with the user during Phase 12
     * design): merge only moves membership rows, the Host archives a source
     * Class separately via the existing Phase 2/7 action if desired. A source
     * id equal to the target is silently skipped (no-op), not an error — the
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
            List<ClassMembership> memberships = classMembershipRepository.findByStudentClass_PublicId(sourceClassPublicId);
            for (ClassMembership membership : memberships) {
                membership.setStudentClass(targetClass);
                ClassMembership saved = classMembershipRepository.save(membership);
                movedStudentPublicIds.add(saved.getStudentPublicId());

                outboxWriter.write(AdminConstants.AGGREGATE_CLASS, targetClassPublicId.toString(),
                        AdminConstants.EVENT_STUDENT_TRANSFERRED_CLASS,
                        new StudentTransferredClassEvent(saved.getPublicId(), sourceClassPublicId,
                                targetClassPublicId, saved.getStudentPublicId(), caller.tenantId()),
                        caller.tenantId());
            }
        }

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, targetClassPublicId.toString(),
                AdminConstants.EVENT_CLASSES_MERGED,
                new ClassesMergedEvent(targetClassPublicId, request.sourceClassPublicIds(), movedStudentPublicIds,
                        caller.tenantId()),
                caller.tenantId());
        if (!movedStudentPublicIds.isEmpty()) {
            auditLogService.record(caller, AdminConstants.AGGREGATE_CLASS, targetClassPublicId.toString(),
                    AdminConstants.EVENT_CLASSES_MERGED,
                    "Merged " + request.sourceClassPublicIds().size() + " Class(es) into \"" + targetClass.getName()
                            + "\" (" + movedStudentPublicIds.size() + " student(s) moved)");
        }
        return new MergeClassesResponse(targetClassPublicId, request.sourceClassPublicIds(), movedStudentPublicIds);
    }

    /**
     * Creates a new {@code StudentClass} under the SAME Program as the source
     * (never a different one — splitting shouldn't relocate a cohort across
     * the academic hierarchy), then moves the given subset of the source's
     * students into it. Any {@code studentPublicId} in the request that isn't
     * actually a member of the source Class is silently ignored (the
     * repository query only ever returns real matches) rather than erroring —
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

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, savedNewClass.getPublicId().toString(),
                AdminConstants.EVENT_CLASS_CREATED,
                new ClassCreatedEvent(savedNewClass.getPublicId(), programPublicId, caller.tenantId(),
                        savedNewClass.getName()),
                caller.tenantId());

        List<ClassMembership> membershipsToMove = classMembershipRepository
                .findByStudentClass_PublicIdAndStudentPublicIdIn(sourceClassPublicId, request.studentPublicIds());
        List<UUID> movedStudentPublicIds = new ArrayList<>();
        for (ClassMembership membership : membershipsToMove) {
            membership.setStudentClass(savedNewClass);
            ClassMembership saved = classMembershipRepository.save(membership);
            movedStudentPublicIds.add(saved.getStudentPublicId());

            outboxWriter.write(AdminConstants.AGGREGATE_CLASS, savedNewClass.getPublicId().toString(),
                    AdminConstants.EVENT_STUDENT_TRANSFERRED_CLASS,
                    new StudentTransferredClassEvent(saved.getPublicId(), sourceClassPublicId,
                            savedNewClass.getPublicId(), saved.getStudentPublicId(), caller.tenantId()),
                    caller.tenantId());
        }

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, savedNewClass.getPublicId().toString(),
                AdminConstants.EVENT_CLASS_SPLIT,
                new ClassSplitEvent(sourceClassPublicId, savedNewClass.getPublicId(), movedStudentPublicIds,
                        caller.tenantId()),
                caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_CLASS, savedNewClass.getPublicId().toString(),
                AdminConstants.EVENT_CLASS_SPLIT,
                "Split Class into new Class \"" + savedNewClass.getName() + "\" (" + movedStudentPublicIds.size()
                        + " student(s) moved)");
        return new SplitClassResponse(StudentClassMapper.toResponse(savedNewClass, programPublicId),
                movedStudentPublicIds);
    }

    /**
     * Tenant-wide roster, optionally filtered by Program — the shared data
     * source Phase 7/10/13 reuse. {@code caller.tenantId()} is always forwarded
     * into the repository query, never checked separately from the program
     * filter (Design Constraint — a decoupled check here would be a
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

        outboxWriter.write(AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_CLASS_STATUS_CHANGED,
                new ClassStatusChangedEvent(classPublicId, programPublicId, caller.tenantId(), target.name()),
                caller.tenantId());
        auditLogService.record(caller, AdminConstants.AGGREGATE_CLASS, classPublicId.toString(),
                AdminConstants.EVENT_CLASS_STATUS_CHANGED, "Changed Class status to " + target.name());
        return StudentClassMapper.toResponse(studentClass, programPublicId);
    }

    /**
     * Loads a Program strictly by (organizationPublicId, programPublicId,
     * caller's tenant) — same "wrong scope looks like not-found" pattern as
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
     * classPublicId, caller's tenant) — a class id that exists but belongs to a
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

    /** Loads a Class by (classPublicId, tenant) only — used to validate a transfer target, which may be under a different Program. */
    private StudentClass loadClassForTenant(UUID classPublicId, UUID tenantId) {
        StudentClass studentClass = studentClassRepository.findByPublicId(classPublicId)
                .orElseThrow(StudentClassNotFoundException::new);
        if (!studentClass.getProgram().getOrganization().getTenant().getPublicId().equals(tenantId)) {
            throw new StudentClassNotFoundException();
        }
        return studentClass;
    }

    private ClassMembership loadMembershipOwned(UUID classPublicId, UUID membershipPublicId) {
        ClassMembership membership = classMembershipRepository.findByPublicId(membershipPublicId)
                .orElseThrow(ClassMembershipNotFoundException::new);
        if (!membership.getStudentClass().getPublicId().equals(classPublicId)) {
            throw new ClassMembershipNotFoundException();
        }
        return membership;
    }
}
