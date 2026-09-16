package com.pte.enrollment.internal.service;

import com.pte.enrollment.domain.ClassMembership;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.StudentClass;
import com.pte.enrollment.internal.dto.request.BulkAssignStudentsRequest;
import com.pte.enrollment.internal.exception.StudentNotFoundException;
import com.pte.enrollment.internal.repository.ClassMembershipRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.StudentClassRepository;
import com.pte.identity.internal.service.IdentityService;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassServiceTenantIsolationTest {

    @Mock
    private StudentClassRepository studentClassRepository;

    @Mock
    private ClassMembershipRepository classMembershipRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private IdentityService identityService;

    @Mock
    private AuditLogService auditLogService;

    private ClassService service;

    @BeforeEach
    void setUp() {
        service = new ClassService(studentClassRepository, classMembershipRepository, programRepository,
                identityService, auditLogService);
    }

    @Test
    void bulkAssignCrossTenantStudentId_rejectsAndCreatesNothing() {
        UUID callerTenantId = UUID.randomUUID();
        UUID foreignTenantId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID foreignStudentId = UUID.randomUUID();

        StudentClass studentClass = new StudentClass();
        studentClass.setPublicId(classId);
        Program program = new Program();
        program.setPublicId(programId);
        Tenant tenant = new Tenant();
        tenant.setPublicId(callerTenantId);
        Organization organization = new Organization();
        organization.setPublicId(organizationId);
        organization.setTenant(tenant);
        program.setOrganization(organization);
        studentClass.setProgram(program);

        when(studentClassRepository.findByPublicId(classId)).thenReturn(Optional.of(studentClass));
        when(identityService.getTenantOf(foreignStudentId)).thenReturn(Optional.of(foreignTenantId));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> service.bulkAssign(organizationId, programId, classId,
                new BulkAssignStudentsRequest(List.of(foreignStudentId)), caller))
                .isInstanceOf(StudentNotFoundException.class);

        verify(classMembershipRepository, never()).findByTenantIdAndStudentPublicIdIn(any(), any());
        verify(classMembershipRepository, never()).saveAll(any());
    }
}
