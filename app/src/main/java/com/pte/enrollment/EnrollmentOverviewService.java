package com.pte.enrollment;

import com.pte.enrollment.domain.enums.ProgramStatus;
import com.pte.enrollment.internal.repository.ClassStudentRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.TrainingClassRepository;
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
    private final TrainingClassRepository trainingClassRepository;
    private final ClassStudentRepository classStudentRepository;

    public EnrollmentOverviewService(ProgramRepository programRepository,
            TrainingClassRepository trainingClassRepository, ClassStudentRepository classStudentRepository) {
        this.programRepository = programRepository;
        this.trainingClassRepository = trainingClassRepository;
        this.classStudentRepository = classStudentRepository;
    }

    /** Active programs and classes plus the number of learners placed in a class. */
    public record AcademicCounts(long programs, long activeClasses, long assignedLearners) {
    }

    @Transactional(readOnly = true)
    public AcademicCounts academicCounts(UUID tenantId) {
        return new AcademicCounts(
                programRepository.countByOrganization_PublicIdAndStatusAndDeletedFalse(
                        tenantId, ProgramStatus.ACTIVE),
                trainingClassRepository.countActiveForTenant(tenantId),
                classStudentRepository.countAssignedForTenant(tenantId));
    }
}
