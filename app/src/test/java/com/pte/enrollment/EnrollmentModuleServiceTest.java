package com.pte.enrollment;

import com.pte.enrollment.domain.ClassMembership;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.StudentClass;
import com.pte.enrollment.internal.exception.StudentClassNotFoundException;
import com.pte.enrollment.internal.repository.ClassMembershipRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.StudentClassRepository;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * First public facade of the {@code enrollment} module (Plan B, Phase 4) —
 * {@code session} is the first-ever caller across the module boundary
 * ({@code session → enrollment}, a new one-way edge {@code ModuleStructureTest}
 * must accept).
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentModuleServiceTest {

    @Mock
    private StudentClassRepository studentClassRepository;
    @Mock
    private ClassMembershipRepository classMembershipRepository;
    @Mock
    private ProgramRepository programRepository;

    private EnrollmentModuleService service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentModuleService(studentClassRepository, classMembershipRepository, programRepository);
    }

    private StudentClass classForTenant(UUID tenantPublicId) {
        Tenant tenant = new Tenant();
        tenant.setPublicId(tenantPublicId);
        Organization organization = new Organization();
        organization.setTenant(tenant);
        Program program = new Program();
        program.setOrganization(organization);
        StudentClass studentClass = new StudentClass();
        studentClass.setProgram(program);
        return studentClass;
    }

    @Test
    void findActiveStudentPublicIds_returnsAllMembersOfClass() {
        UUID tenantId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classForTenant(tenantId);
        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        ClassMembership m1 = new ClassMembership();
        m1.setStudentPublicId(UUID.randomUUID());
        ClassMembership m2 = new ClassMembership();
        m2.setStudentPublicId(UUID.randomUUID());
        when(classMembershipRepository.findByTenantIdAndStudentClass_PublicId(tenantId, classPublicId))
                .thenReturn(List.of(m1, m2));

        List<UUID> result = service.findActiveStudentPublicIds(tenantId, classPublicId);

        assertThat(result).containsExactlyInAnyOrder(m1.getStudentPublicId(), m2.getStudentPublicId());
    }

    @Test
    void findActiveStudentPublicIds_classFromOtherTenant_throwsNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classForTenant(otherTenantId);
        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));

        assertThatThrownBy(() -> service.findActiveStudentPublicIds(tenantId, classPublicId))
                .isInstanceOf(StudentClassNotFoundException.class);
    }

    @Test
    void findActiveStudentPublicIds_unknownClass_throwsNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findActiveStudentPublicIds(tenantId, classPublicId))
                .isInstanceOf(StudentClassNotFoundException.class);
    }

    @Test
    void findActiveStudentPublicIdsByProgram_requiresActiveProgramInTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programForTenant(tenantId, programPublicId);
        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(classMembershipRepository
                .findByStudentClass_Program_Organization_Tenant_PublicIdAndStudentClass_Program_PublicId(
                        tenantId, programPublicId))
                .thenReturn(List.of());

        assertThat(service.findActiveStudentPublicIdsByProgram(tenantId, programPublicId)).isEmpty();
    }

    @Test
    void findActiveStudentPublicIdsByProgram_rejectsProgramFromOtherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programForTenant(UUID.randomUUID(), programPublicId);
        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> service.findActiveStudentPublicIdsByProgram(tenantId, programPublicId))
                .isInstanceOf(com.pte.enrollment.internal.exception.ProgramNotFoundException.class);
    }

    private Program programForTenant(UUID tenantId, UUID programPublicId) {
        Tenant tenant = new Tenant();
        tenant.setPublicId(tenantId);
        Organization organization = new Organization();
        organization.setTenant(tenant);
        Program program = new Program();
        program.setPublicId(programPublicId);
        program.setOrganization(organization);
        return program;
    }
}
