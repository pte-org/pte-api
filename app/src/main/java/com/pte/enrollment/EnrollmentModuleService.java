package com.pte.enrollment;

import com.pte.enrollment.domain.ClassMembership;
import com.pte.enrollment.domain.enums.ClassStatus;
import com.pte.enrollment.domain.enums.ProgramStatus;
import com.pte.enrollment.domain.StudentClass;
import com.pte.enrollment.internal.exception.ProgramNotFoundException;
import com.pte.enrollment.internal.exception.StudentClassNotFoundException;
import com.pte.enrollment.internal.repository.ClassMembershipRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.StudentClassRepository;
import com.pte.identity.IdentityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The first public facade of {@code enrollment} for another module to call —
 * {@code session} (Plan B, Phase 4: assign a Class to an exam session).
 * {@code internal/} repositories and every other service stay hidden.
 */
@Service
public class EnrollmentModuleService {

    private final StudentClassRepository studentClassRepository;
    private final ClassMembershipRepository classMembershipRepository;
    private final ProgramRepository programRepository;
    private final IdentityService identityService;

    @org.springframework.beans.factory.annotation.Autowired
    public EnrollmentModuleService(StudentClassRepository studentClassRepository,
                                   ClassMembershipRepository classMembershipRepository,
                                   ProgramRepository programRepository,
                                   IdentityService identityService) {
        this.studentClassRepository = studentClassRepository;
        this.classMembershipRepository = classMembershipRepository;
        this.programRepository = programRepository;
        this.identityService = identityService;
    }

    public EnrollmentModuleService(StudentClassRepository studentClassRepository,
                                   ClassMembershipRepository classMembershipRepository,
                                   ProgramRepository programRepository) {
        this(studentClassRepository, classMembershipRepository, programRepository, null);
    }

    /** Compatibility constructor for focused enrollment facade unit tests. */
    public EnrollmentModuleService(StudentClassRepository studentClassRepository,
                                   ClassMembershipRepository classMembershipRepository) {
        this(studentClassRepository, classMembershipRepository, null);
    }

    /** Returns only the student's current membership; it is not used for attempt history. */
    @Transactional(readOnly = true)
    public StudentAssignmentView findCurrentStudentAssignment(UUID tenantId, UUID studentPublicId) {
        if (tenantId == null || studentPublicId == null) {
            return null;
        }
        return classMembershipRepository.findByTenantIdAndStudentPublicId(tenantId, studentPublicId)
                .filter(membership -> !membership.isDeleted())
                .map(membership -> new StudentAssignmentView(
                        membership.getStudentClass().getPublicId(),
                        membership.getStudentClass().getName(),
                        membership.getStudentClass().getProgram().getPublicId(),
                        membership.getStudentClass().getProgram().getName()))
                .orElse(null);
    }

    /**
     * Every current member of {@code classPublicId}, verified to belong to
     * {@code tenantId} first (same "wrong scope looks like not-found" pattern
     * as {@code ClassService.loadClassForTenant} — a class id from another
     * tenant is treated as not-found, never silently served).
     */
    @Transactional(readOnly = true)
    public List<UUID> findActiveStudentPublicIds(UUID tenantId, UUID classPublicId) {
        StudentClass studentClass = studentClassRepository.findByPublicId(classPublicId)
                .orElseThrow(StudentClassNotFoundException::new);
        if (!studentClass.getProgram().getOrganization().getPublicId().equals(tenantId)) {
            throw new StudentClassNotFoundException();
        }
        if (studentClass.isDeleted() || studentClass.getStatus() != ClassStatus.ACTIVE
                || studentClass.getProgram().isDeleted()
                || studentClass.getProgram().getStatus() != ProgramStatus.ACTIVE
                || !studentClass.getProgram().isCurrentlyActive(LocalDate.now())) {
            throw new StudentClassNotFoundException();
        }
        List<UUID> studentPublicIds = classMembershipRepository
                .findByTenantIdAndStudentClass_PublicId(tenantId, classPublicId).stream()
                .filter(membership -> !membership.isDeleted())
                .map(ClassMembership::getStudentPublicId)
                .toList();
        return filterActiveStudents(tenantId, studentPublicIds);
    }

    /** The program a tenant's class belongs to; a class of another tenant looks like not-found. */
    @Transactional(readOnly = true)
    public UUID findProgramPublicIdOfClass(UUID tenantId, UUID classPublicId) {
        StudentClass studentClass = studentClassRepository.findByPublicId(classPublicId)
                .orElseThrow(StudentClassNotFoundException::new);
        if (!studentClass.getProgram().getOrganization().getPublicId().equals(tenantId)) {
            throw new StudentClassNotFoundException();
        }
        return studentClass.getProgram().getPublicId();
    }

    /** Resolves the union of current members in every non-archived class of a tenant program. */
    @Transactional(readOnly = true)
    public List<UUID> findActiveStudentPublicIdsByProgram(UUID tenantId, UUID programPublicId) {
        if (programRepository != null) {
            programRepository.findByPublicId(programPublicId)
                    .filter(program -> program.getOrganization() != null
                            && tenantId.equals(program.getOrganization().getPublicId()))
                    .filter(program -> !program.isDeleted()
                            && program.getStatus() == ProgramStatus.ACTIVE
                            && program.isCurrentlyActive(LocalDate.now()))
                    .orElseThrow(ProgramNotFoundException::new);
        }
        List<UUID> studentPublicIds = classMembershipRepository
                .findByStudentClass_Program_Organization_PublicIdAndStudentClass_Program_PublicId(
                        tenantId, programPublicId).stream()
                .map(ClassMembership::getStudentPublicId)
                .distinct()
                .toList();
        return filterActiveStudents(tenantId, studentPublicIds);
    }

    private List<UUID> filterActiveStudents(UUID tenantId, List<UUID> studentPublicIds) {
        if (identityService == null || studentPublicIds.isEmpty()) {
            return studentPublicIds;
        }
        var activeIds = identityService.findActiveStudentPublicIds(tenantId, studentPublicIds);
        return studentPublicIds.stream().filter(activeIds::contains).toList();
    }
}
