package com.pte.enrollment;

import com.pte.enrollment.domain.ClassStudent;
import com.pte.enrollment.domain.enums.ClassStatus;
import com.pte.enrollment.domain.enums.ProgramStatus;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.internal.exception.ProgramNotFoundException;
import com.pte.enrollment.internal.exception.StudentClassNotFoundException;
import com.pte.enrollment.internal.repository.ClassStudentRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.TrainingClassRepository;
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

    private final TrainingClassRepository trainingClassRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ProgramRepository programRepository;
    private final IdentityService identityService;

    @org.springframework.beans.factory.annotation.Autowired
    public EnrollmentModuleService(TrainingClassRepository trainingClassRepository,
                                   ClassStudentRepository classStudentRepository,
                                   ProgramRepository programRepository,
                                   IdentityService identityService) {
        this.trainingClassRepository = trainingClassRepository;
        this.classStudentRepository = classStudentRepository;
        this.programRepository = programRepository;
        this.identityService = identityService;
    }

    public EnrollmentModuleService(TrainingClassRepository trainingClassRepository,
                                   ClassStudentRepository classStudentRepository,
                                   ProgramRepository programRepository) {
        this(trainingClassRepository, classStudentRepository, programRepository, null);
    }

    /** Compatibility constructor for focused enrollment facade unit tests. */
    public EnrollmentModuleService(TrainingClassRepository trainingClassRepository,
                                   ClassStudentRepository classStudentRepository) {
        this(trainingClassRepository, classStudentRepository, null);
    }

    /** Returns only the student's current membership; it is not used for attempt history. */
    @Transactional(readOnly = true)
    public StudentAssignmentView findCurrentStudentAssignment(UUID tenantId, UUID studentPublicId) {
        if (tenantId == null || studentPublicId == null) {
            return null;
        }
        return classStudentRepository.findByOrganizationIdAndStudentPublicId(tenantId, studentPublicId)
                .filter(membership -> !membership.isDeleted())
                .map(membership -> new StudentAssignmentView(
                        membership.getTrainingClass().getPublicId(),
                        membership.getTrainingClass().getName(),
                        membership.getTrainingClass().getProgram().getPublicId(),
                        membership.getTrainingClass().getProgram().getName()))
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
        TrainingClass trainingClass = trainingClassRepository.findByPublicId(classPublicId)
                .orElseThrow(StudentClassNotFoundException::new);
        if (!tenantId.equals(trainingClass.getOrganizationId())) {
            throw new StudentClassNotFoundException();
        }
        if (trainingClass.isDeleted() || trainingClass.getStatus() != ClassStatus.ACTIVE
                || trainingClass.getProgram().isDeleted()
                || trainingClass.getProgram().getStatus() != ProgramStatus.ACTIVE
                || !trainingClass.getProgram().isCurrentlyActive(LocalDate.now())) {
            throw new StudentClassNotFoundException();
        }
        List<UUID> studentPublicIds = classStudentRepository
                .findByOrganizationIdAndTrainingClass_PublicId(tenantId, classPublicId).stream()
                .filter(membership -> !membership.isDeleted())
                .map(ClassStudent::getStudentPublicId)
                .toList();
        return filterActiveStudents(tenantId, studentPublicIds);
    }

    /** The program a tenant's class belongs to; a class of another tenant looks like not-found. */
    @Transactional(readOnly = true)
    public UUID findProgramPublicIdOfClass(UUID tenantId, UUID classPublicId) {
        TrainingClass trainingClass = trainingClassRepository.findByPublicId(classPublicId)
                .orElseThrow(StudentClassNotFoundException::new);
        if (!tenantId.equals(trainingClass.getOrganizationId())) {
            throw new StudentClassNotFoundException();
        }
        return trainingClass.getProgram().getPublicId();
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
        List<UUID> studentPublicIds = classStudentRepository
                .findByTrainingClass_Program_Organization_PublicIdAndTrainingClass_Program_PublicId(
                        tenantId, programPublicId).stream()
                .map(ClassStudent::getStudentPublicId)
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
