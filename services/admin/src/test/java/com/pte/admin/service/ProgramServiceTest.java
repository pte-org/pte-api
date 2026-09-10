package com.pte.admin.service;

import com.pte.admin.constant.AdminConstants;
import com.pte.admin.domain.Organization;
import com.pte.admin.domain.Program;
import com.pte.admin.domain.Tenant;
import com.pte.admin.domain.enums.FacilityType;
import com.pte.admin.domain.enums.ProgramStatus;
import com.pte.admin.domain.event.ProgramArchivedEvent;
import com.pte.admin.domain.event.ProgramCreatedEvent;
import com.pte.admin.domain.event.ProgramStatusChangedEvent;
import com.pte.admin.domain.event.ProgramUpdatedEvent;
import com.pte.admin.domain.exception.OrganizationNotFoundException;
import com.pte.admin.domain.exception.ProgramHasActiveClassesException;
import com.pte.admin.domain.exception.ProgramNameAlreadyUsedException;
import com.pte.admin.domain.exception.ProgramNotFoundException;
import com.pte.admin.dto.request.CreateProgramRequest;
import com.pte.admin.dto.request.UpdateProgramRequest;
import com.pte.admin.dto.response.ClassStudentCountResponse;
import com.pte.admin.dto.response.ProgramDashboardResponse;
import com.pte.admin.dto.response.ProgramResponse;
import com.pte.admin.messaging.outbox.OutboxWriter;
import com.pte.admin.repository.OrganizationRepository;
import com.pte.admin.repository.ProgramRepository;
import com.pte.admin.repository.StudentClassRepository;
import com.pte.common.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramServiceTest {

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private StudentClassRepository studentClassRepository;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private AuditLogService auditLogService;

    private ProgramService service;

    @BeforeEach
    void setUp() {
        service = new ProgramService(programRepository, organizationRepository, studentClassRepository, outboxWriter,
                auditLogService);
    }

    private Tenant tenantWithPublicId(UUID publicId) {
        Tenant tenant = new Tenant();
        tenant.setPublicId(publicId);
        tenant.setName("Acme School");
        return tenant;
    }

    private Organization organizationOf(UUID orgPublicId, Tenant tenant) {
        Organization organization = new Organization();
        organization.setPublicId(orgPublicId);
        organization.setName("Downtown Branch");
        organization.setFacilityType(FacilityType.BRANCH);
        organization.setTenant(tenant);
        return organization;
    }

    private Program programOf(UUID programPublicId, Organization organization) {
        Program program = new Program();
        program.setPublicId(programPublicId);
        program.setName("Khối 12");
        program.setStatus(ProgramStatus.ACTIVE);
        program.setOrganization(organization);
        return program;
    }

    @Test
    void create_savesProgramUnderOrganizationAndWritesOutboxEvent() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        Organization organization = organizationOf(organizationPublicId, tenant);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        CreateProgramRequest request = new CreateProgramRequest("Khối 12", "Grade 12", null, null);

        when(organizationRepository.findByPublicId(organizationPublicId)).thenReturn(Optional.of(organization));
        when(programRepository.existsByOrganization_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                organizationPublicId, "Khối 12")).thenReturn(false);
        when(programRepository.save(any(Program.class))).thenAnswer(invocation -> {
            Program saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        ProgramResponse response = service.create(organizationPublicId, request, caller);

        assertThat(response.publicId()).isNotNull();
        assertThat(response.name()).isEqualTo("Khối 12");
        assertThat(response.organizationPublicId()).isEqualTo(organizationPublicId);
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(outboxWriter).write(eq(AdminConstants.AGGREGATE_PROGRAM), any(), eq(AdminConstants.EVENT_PROGRAM_CREATED),
                any(ProgramCreatedEvent.class), eq(tenantPublicId));
        verify(auditLogService).record(eq(caller), eq(AdminConstants.AGGREGATE_PROGRAM), any(),
                eq(AdminConstants.EVENT_PROGRAM_CREATED), any());
    }

    @Test
    void create_organizationBelongsToDifferentTenant_throwsNotFoundWithoutSaving() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(otherTenantId));
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        when(organizationRepository.findByPublicId(organizationPublicId)).thenReturn(Optional.of(organization));

        assertThatThrownBy(() -> service.create(organizationPublicId,
                new CreateProgramRequest("Khối 12", null, null, null), caller))
                .isInstanceOf(OrganizationNotFoundException.class);
        verify(programRepository, never()).save(any());
    }

    @Test
    void create_duplicateNameWithinSameOrganization_throwsWithoutSaving() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId));
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(organizationRepository.findByPublicId(organizationPublicId)).thenReturn(Optional.of(organization));
        when(programRepository.existsByOrganization_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                organizationPublicId, "Khối 12")).thenReturn(true);

        assertThatThrownBy(() -> service.create(organizationPublicId,
                new CreateProgramRequest("Khối 12", null, null, null), caller))
                .isInstanceOf(ProgramNameAlreadyUsedException.class);
        verify(programRepository, never()).save(any());
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void create_sameNameUnderDifferentOrganizationOfSameTenant_succeeds() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        Organization organization = organizationOf(organizationPublicId, tenant);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(organizationRepository.findByPublicId(organizationPublicId)).thenReturn(Optional.of(organization));
        // Uniqueness is checked scoped to THIS organization only — a different organization's
        // existing "Khối 12" must never block this one.
        when(programRepository.existsByOrganization_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                organizationPublicId, "Khối 12")).thenReturn(false);
        when(programRepository.save(any(Program.class))).thenAnswer(invocation -> {
            Program saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        ProgramResponse response = service.create(organizationPublicId,
                new CreateProgramRequest("Khối 12", null, null, null), caller);

        assertThat(response.name()).isEqualTo("Khối 12");
    }

    @Test
    void list_excludesArchivedPrograms() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId));
        Program activeProgram = programOf(UUID.randomUUID(), organization);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(organizationRepository.findByPublicId(organizationPublicId)).thenReturn(Optional.of(organization));
        when(programRepository.findByOrganization_PublicIdAndDeletedFalseOrderByCreatedAtAsc(organizationPublicId))
                .thenReturn(List.of(activeProgram));

        List<ProgramResponse> responses = service.list(organizationPublicId, caller);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).publicId()).isEqualTo(activeProgram.getPublicId());
    }

    @Test
    void get_programBelongsToDifferentTenant_throwsNotFound() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(otherTenantId));
        Program program = programOf(programPublicId, organization);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> service.get(organizationPublicId, programPublicId, caller))
                .isInstanceOf(ProgramNotFoundException.class);
    }

    @Test
    void get_programBelongsToDifferentOrganizationOfSameTenant_throwsNotFound() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        UUID actualOrganizationPublicId = UUID.randomUUID();
        UUID requestedOrganizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(actualOrganizationPublicId, tenant);
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organization);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> service.get(requestedOrganizationPublicId, programPublicId, caller))
                .isInstanceOf(ProgramNotFoundException.class);
    }

    @Test
    void update_changesFieldsAndWritesOutboxEvent() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId));
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organization);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        ProgramResponse response = service.update(organizationPublicId, programPublicId,
                new UpdateProgramRequest("Khối 12A", "Updated", LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31)),
                caller);

        assertThat(response.name()).isEqualTo("Khối 12A");
        assertThat(response.description()).isEqualTo("Updated");
        verify(outboxWriter).write(eq(AdminConstants.AGGREGATE_PROGRAM), eq(programPublicId.toString()),
                eq(AdminConstants.EVENT_PROGRAM_UPDATED), any(ProgramUpdatedEvent.class), eq(tenantPublicId));
        verify(auditLogService).record(eq(caller), eq(AdminConstants.AGGREGATE_PROGRAM), eq(programPublicId.toString()),
                eq(AdminConstants.EVENT_PROGRAM_UPDATED), any());
    }

    @Test
    void suspend_activeProgram_transitionsAndWritesOutboxEvent() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId));
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organization);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        ProgramResponse response = service.suspend(organizationPublicId, programPublicId, caller);

        assertThat(response.status()).isEqualTo("SUSPENDED");
        verify(outboxWriter).write(eq(AdminConstants.AGGREGATE_PROGRAM), eq(programPublicId.toString()),
                eq(AdminConstants.EVENT_PROGRAM_STATUS_CHANGED), any(ProgramStatusChangedEvent.class), eq(tenantPublicId));
        verify(auditLogService).record(eq(caller), eq(AdminConstants.AGGREGATE_PROGRAM), eq(programPublicId.toString()),
                eq(AdminConstants.EVENT_PROGRAM_STATUS_CHANGED), any());
    }

    @Test
    void suspend_alreadySuspended_isIdempotentNoOp() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId));
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organization);
        program.setStatus(ProgramStatus.SUSPENDED);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        ProgramResponse response = service.suspend(organizationPublicId, programPublicId, caller);

        assertThat(response.status()).isEqualTo("SUSPENDED");
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void deactivate_activeProgram_transitionsToInactive() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId));
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organization);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        ProgramResponse response = service.deactivate(organizationPublicId, programPublicId, caller);

        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void activate_inactiveProgram_transitionsBackToActive() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId));
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organization);
        program.setStatus(ProgramStatus.INACTIVE);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        ProgramResponse response = service.activate(organizationPublicId, programPublicId, caller);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void archive_activeProgram_marksDeletedButStaysFetchable() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId));
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organization);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        ProgramResponse response = service.archive(organizationPublicId, programPublicId, caller);

        assertThat(program.isDeleted()).isTrue();
        assertThat(response.publicId()).isEqualTo(programPublicId);
        verify(outboxWriter).write(eq(AdminConstants.AGGREGATE_PROGRAM), eq(programPublicId.toString()),
                eq(AdminConstants.EVENT_PROGRAM_ARCHIVED), any(ProgramArchivedEvent.class), eq(tenantPublicId));
        verify(auditLogService).record(eq(caller), eq(AdminConstants.AGGREGATE_PROGRAM), eq(programPublicId.toString()),
                eq(AdminConstants.EVENT_PROGRAM_ARCHIVED), any());

        // Get-by-id still works after archive — archive is a visibility flag on `list`, not a hard delete.
        ProgramResponse getResponse = service.get(organizationPublicId, programPublicId, caller);
        assertThat(getResponse.publicId()).isEqualTo(programPublicId);
    }

    @Test
    void archive_alreadyArchived_isIdempotentNoOp() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId));
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organization);
        program.setDeleted(true);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        service.archive(organizationPublicId, programPublicId, caller);

        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void archive_hasActiveClasses_throwsWithoutArchiving() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId));
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organization);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(studentClassRepository.existsByProgram_PublicIdAndDeletedFalse(programPublicId)).thenReturn(true);

        assertThatThrownBy(() -> service.archive(organizationPublicId, programPublicId, caller))
                .isInstanceOf(ProgramHasActiveClassesException.class);

        assertThat(program.isDeleted()).isFalse();
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void getDashboard_returnsClassAndStudentCountsFromOneGroupedQuery() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId));
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organization);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        List<ClassStudentCountResponse> rows = List.of(
                new ClassStudentCountResponse(UUID.randomUUID(), "12A1", 20L),
                new ClassStudentCountResponse(UUID.randomUUID(), "12A2", 15L),
                new ClassStudentCountResponse(UUID.randomUUID(), "12A3", 0L));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(studentClassRepository.countStudentsByClassForProgram(programPublicId)).thenReturn(rows);

        ProgramDashboardResponse response = service.getDashboard(organizationPublicId, programPublicId, caller);

        assertThat(response.programPublicId()).isEqualTo(programPublicId);
        assertThat(response.classCount()).isEqualTo(3);
        assertThat(response.studentCount()).isEqualTo(35L);
        assertThat(response.classes()).isEqualTo(rows);
    }

    @Test
    void getDashboard_programBelongsToDifferentTenant_throwsNotFound() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenantWithPublicId(otherTenantId));
        Program program = programOf(programPublicId, organization);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> service.getDashboard(organizationPublicId, programPublicId, caller))
                .isInstanceOf(ProgramNotFoundException.class);
        verify(studentClassRepository, never()).countStudentsByClassForProgram(any());
    }
}
