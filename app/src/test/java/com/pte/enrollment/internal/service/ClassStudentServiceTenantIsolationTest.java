package com.pte.enrollment.internal.service;

import com.pte.enrollment.domain.ClassStudent;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.internal.dto.request.BulkAssignStudentsRequest;
import com.pte.enrollment.internal.exception.StudentNotFoundException;
import com.pte.enrollment.internal.repository.ClassStudentRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.TrainingClassRepository;
import com.pte.identity.IdentityService;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.domain.Organization;
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
class ClassStudentServiceTenantIsolationTest {

    @Mock
    private TrainingClassRepository trainingClassRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private IdentityService identityService;

    @Mock
    private AuditLogService auditLogService;

    private ClassStudentService service;

    @BeforeEach
    void setUp() {
        service = new ClassStudentService(classStudentRepository, trainingClassRepository,
                new ClassService(trainingClassRepository, programRepository, auditLogService),
                identityService, auditLogService);
    }

    @Test
    void bulkAssignCrossTenantStudentId_rejectsAndCreatesNothing() {
        UUID callerTenantId = UUID.randomUUID();
        UUID foreignTenantId = UUID.randomUUID();
        UUID organizationId = callerTenantId;
        UUID programId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID foreignStudentId = UUID.randomUUID();

        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setPublicId(classId);
        Program program = new Program();
        program.setPublicId(programId);
        Organization organization = new Organization();
        organization.setPublicId(organizationId);
        program.setOrganization(organization);
        trainingClass.setProgram(program);

        when(trainingClassRepository.findByPublicId(classId)).thenReturn(Optional.of(trainingClass));
        when(identityService.isMemberOf(foreignStudentId, callerTenantId)).thenReturn(false);

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> service.bulkAssign(organizationId, programId, classId,
                new BulkAssignStudentsRequest(List.of(foreignStudentId)), caller))
                .isInstanceOf(StudentNotFoundException.class);

        verify(classStudentRepository, never()).findByOrganizationIdAndStudentPublicIdIn(any(), any());
        verify(classStudentRepository, never()).saveAll(any());
    }
}
