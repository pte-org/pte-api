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

    @org.springframework.beans.factory.annotation.Autowired
    public EnrollmentModuleService(StudentClassRepository studentClassRepository,
                                   ClassMembershipRepository classMembershipRepository,
                                   ProgramRepository programRepository) {
        this.studentClassRepository = studentClassRepository;
        this.classMembershipRepository = classMembershipRepository;
        this.programRepository = programRepository;
    }

    /** Compatibility constructor for focused enrollment facade unit tests. */
    public EnrollmentModuleService(StudentClassRepository studentClassRepository,
                                   ClassMembershipRepository classMembershipRepository) {
        this(studentClassRepository, classMembershipRepository, null);
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
        if (!studentClass.getProgram().getOrganization().getTenant().getPublicId().equals(tenantId)) {
            throw new StudentClassNotFoundException();
        }
        if (studentClass.isDeleted() || studentClass.getStatus() != ClassStatus.ACTIVE
                || studentClass.getProgram().isDeleted()
                || studentClass.getProgram().getStatus() != ProgramStatus.ACTIVE
                || !studentClass.getProgram().isCurrentlyActive(LocalDate.now())) {
            throw new StudentClassNotFoundException();
        }
        return classMembershipRepository.findByTenantIdAndStudentClass_PublicId(tenantId, classPublicId).stream()
                .filter(membership -> !membership.isDeleted())
                .map(ClassMembership::getStudentPublicId)
                .toList();
    }

    /** Resolves the union of current members in every non-archived class of a tenant program. */
    @Transactional(readOnly = true)
    public List<UUID> findActiveStudentPublicIdsByProgram(UUID tenantId, UUID programPublicId) {
        if (programRepository != null) {
            programRepository.findByPublicId(programPublicId)
                    .filter(program -> program.getOrganization() != null
                            && program.getOrganization().getTenant() != null
                            && tenantId.equals(program.getOrganization().getTenant().getPublicId()))
                    .filter(program -> !program.isDeleted()
                            && program.getStatus() == ProgramStatus.ACTIVE
                            && program.isCurrentlyActive(LocalDate.now()))
                    .orElseThrow(ProgramNotFoundException::new);
        }
        return classMembershipRepository
                .findByStudentClass_Program_Organization_Tenant_PublicIdAndStudentClass_Program_PublicId(
                        tenantId, programPublicId).stream()
                .map(ClassMembership::getStudentPublicId)
                .distinct()
                .toList();
    }
}
