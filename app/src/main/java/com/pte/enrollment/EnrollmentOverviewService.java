package com.pte.enrollment;

import com.pte.enrollment.domain.enums.ProgramStatus;
import com.pte.enrollment.internal.repository.ClassMembershipRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.StudentClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only academic-structure counts for dashboards. Depends only on this
 * module's own repositories, so it adds no cross-module dependency to
 * {@code enrollment}. {@code tenantId} is the tenant public id from the token.
 */
@Service
public class EnrollmentOverviewService {

    private final ProgramRepository programRepository;
    private final StudentClassRepository studentClassRepository;
    private final ClassMembershipRepository classMembershipRepository;

    public EnrollmentOverviewService(ProgramRepository programRepository,
            StudentClassRepository studentClassRepository, ClassMembershipRepository classMembershipRepository) {
        this.programRepository = programRepository;
        this.studentClassRepository = studentClassRepository;
        this.classMembershipRepository = classMembershipRepository;
    }

    /** Active programs and classes plus the number of learners placed in a class. */
    public record AcademicCounts(long programs, long activeClasses, long assignedLearners) {
    }

    @Transactional(readOnly = true)
    public AcademicCounts academicCounts(UUID tenantId) {
        return new AcademicCounts(
                programRepository.countByOrganization_Tenant_PublicIdAndStatusAndDeletedFalse(
                        tenantId, ProgramStatus.ACTIVE),
                studentClassRepository.countActiveForTenant(tenantId),
                classMembershipRepository.countAssignedForTenant(tenantId));
    }
}
