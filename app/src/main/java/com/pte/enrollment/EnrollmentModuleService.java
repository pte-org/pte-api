package com.pte.enrollment;

import com.pte.enrollment.domain.ClassMembership;
import com.pte.enrollment.domain.StudentClass;
import com.pte.enrollment.internal.exception.StudentClassNotFoundException;
import com.pte.enrollment.internal.repository.ClassMembershipRepository;
import com.pte.enrollment.internal.repository.StudentClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public EnrollmentModuleService(StudentClassRepository studentClassRepository,
                                   ClassMembershipRepository classMembershipRepository) {
        this.studentClassRepository = studentClassRepository;
        this.classMembershipRepository = classMembershipRepository;
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
        return classMembershipRepository.findByTenantIdAndStudentClass_PublicId(tenantId, classPublicId).stream()
                .map(ClassMembership::getStudentPublicId)
                .toList();
    }
}
