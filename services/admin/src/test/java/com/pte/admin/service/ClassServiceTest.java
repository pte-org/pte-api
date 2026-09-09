package com.pte.admin.service;

import com.pte.admin.constant.AdminConstants;
import com.pte.admin.domain.ClassMembership;
import com.pte.admin.domain.Organization;
import com.pte.admin.domain.Program;
import com.pte.admin.domain.StudentClass;
import com.pte.admin.domain.Tenant;
import com.pte.admin.domain.enums.ClassStatus;
import com.pte.admin.domain.enums.FacilityType;
import com.pte.admin.domain.event.ClassArchivedEvent;
import com.pte.admin.domain.event.ClassCreatedEvent;
import com.pte.admin.domain.event.ClassStatusChangedEvent;
import com.pte.admin.domain.event.StudentAssignedToClassEvent;
import com.pte.admin.domain.event.StudentTransferredClassEvent;
import com.pte.admin.domain.event.StudentUnassignedFromClassEvent;
import com.pte.admin.domain.exception.ClassHasActiveMembersException;
import com.pte.admin.domain.exception.ClassMembershipNotFoundException;
import com.pte.admin.domain.exception.ProgramNotFoundException;
import com.pte.admin.domain.exception.StudentAlreadyInClassException;
import com.pte.admin.domain.exception.StudentClassNameAlreadyUsedException;
import com.pte.admin.domain.exception.StudentClassNotFoundException;
import com.pte.admin.dto.request.AssignStudentRequest;
import com.pte.admin.dto.request.BulkAssignStudentsRequest;
import com.pte.admin.dto.request.CreateClassRequest;
import com.pte.admin.dto.request.TransferStudentRequest;
import com.pte.admin.dto.request.UpdateClassRequest;
import com.pte.admin.dto.response.BulkAssignStudentsResponse;
import com.pte.admin.dto.response.ClassMembershipResponse;
import com.pte.admin.dto.response.ClassResponse;
import com.pte.admin.messaging.outbox.OutboxWriter;
import com.pte.admin.repository.ClassMembershipRepository;
import com.pte.admin.repository.ProgramRepository;
import com.pte.admin.repository.StudentClassRepository;
import com.pte.common.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassServiceTest {

    @Mock
    private StudentClassRepository studentClassRepository;

    @Mock
    private ClassMembershipRepository classMembershipRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private OutboxWriter outboxWriter;

    private ClassService service;

    @BeforeEach
    void setUp() {
        service = new ClassService(studentClassRepository, classMembershipRepository, programRepository, outboxWriter);
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
        program.setOrganization(organization);
        return program;
    }

    private StudentClass classOf(UUID classPublicId, Program program) {
        StudentClass studentClass = new StudentClass();
        studentClass.setPublicId(classPublicId);
        studentClass.setName("12A1");
        studentClass.setStatus(ClassStatus.ACTIVE);
        studentClass.setProgram(program);
        return studentClass;
    }

    private ClassMembership membershipOf(UUID membershipPublicId, StudentClass studentClass, UUID studentPublicId) {
        ClassMembership membership = new ClassMembership();
        membership.setPublicId(membershipPublicId);
        membership.setStudentClass(studentClass);
        membership.setStudentPublicId(studentPublicId);
        return membership;
    }

    // --- create ---

    @Test
    void create_savesClassUnderProgramAndWritesOutboxEvent() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(studentClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(programPublicId, "12A1"))
                .thenReturn(false);
        when(studentClassRepository.save(any(StudentClass.class))).thenAnswer(invocation -> {
            StudentClass saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        ClassResponse response = service.create(organizationPublicId, programPublicId,
                new CreateClassRequest("12A1"), caller);

        assertThat(response.publicId()).isNotNull();
        assertThat(response.name()).isEqualTo("12A1");
        assertThat(response.programPublicId()).isEqualTo(programPublicId);
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(outboxWriter).write(eq(AdminConstants.AGGREGATE_CLASS), any(), eq(AdminConstants.EVENT_CLASS_CREATED),
                any(ClassCreatedEvent.class), eq(tenantPublicId));
    }

    @Test
    void create_programBelongsToDifferentTenant_throwsNotFoundWithoutSaving() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(otherTenantId)));
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> service.create(organizationPublicId, programPublicId,
                new CreateClassRequest("12A1"), caller))
                .isInstanceOf(ProgramNotFoundException.class);
        verify(studentClassRepository, never()).save(any());
    }

    @Test
    void create_duplicateNameWithinSameProgram_throwsWithoutSaving() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(studentClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(programPublicId, "12A1"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(organizationPublicId, programPublicId,
                new CreateClassRequest("12A1"), caller))
                .isInstanceOf(StudentClassNameAlreadyUsedException.class);
        verify(studentClassRepository, never()).save(any());
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    // --- get / tenant-mismatch ---

    @Test
    void get_classBelongsToDifferentTenant_throwsNotFound() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(otherTenantId)));
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));

        assertThatThrownBy(() -> service.get(organizationPublicId, programPublicId, classPublicId, caller))
                .isInstanceOf(StudentClassNotFoundException.class);
    }

    @Test
    void get_classBelongsToDifferentProgramOfSameTenant_throwsNotFound() {
        UUID tenantPublicId = UUID.randomUUID();
        Organization organization = organizationOf(UUID.randomUUID(), tenantWithPublicId(tenantPublicId));
        UUID actualProgramPublicId = UUID.randomUUID();
        UUID requestedProgramPublicId = UUID.randomUUID();
        Program program = programOf(actualProgramPublicId, organization);
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));

        assertThatThrownBy(() -> service.get(organization.getPublicId(), requestedProgramPublicId, classPublicId, caller))
                .isInstanceOf(StudentClassNotFoundException.class);
    }

    // --- update ---

    @Test
    void update_changesNameAndWritesOutboxEvent() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));

        ClassResponse response = service.update(organizationPublicId, programPublicId, classPublicId,
                new UpdateClassRequest("12A2"), caller);

        assertThat(response.name()).isEqualTo("12A2");
        verify(outboxWriter).write(eq(AdminConstants.AGGREGATE_CLASS), eq(classPublicId.toString()),
                eq(AdminConstants.EVENT_CLASS_UPDATED), any(), eq(tenantPublicId));
    }

    // --- status/archive ---

    @Test
    void suspend_activeClass_transitionsAndWritesOutboxEvent() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));

        ClassResponse response = service.suspend(organizationPublicId, programPublicId, classPublicId, caller);

        assertThat(response.status()).isEqualTo("SUSPENDED");
        verify(outboxWriter).write(eq(AdminConstants.AGGREGATE_CLASS), eq(classPublicId.toString()),
                eq(AdminConstants.EVENT_CLASS_STATUS_CHANGED), any(ClassStatusChangedEvent.class), eq(tenantPublicId));
    }

    @Test
    void archive_activeClassWithNoMembers_marksDeletedButStaysFetchable() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(classMembershipRepository.existsByStudentClass_PublicId(classPublicId)).thenReturn(false);

        ClassResponse response = service.archive(organizationPublicId, programPublicId, classPublicId, caller);

        assertThat(studentClass.isDeleted()).isTrue();
        assertThat(response.publicId()).isEqualTo(classPublicId);
        verify(outboxWriter).write(eq(AdminConstants.AGGREGATE_CLASS), eq(classPublicId.toString()),
                eq(AdminConstants.EVENT_CLASS_ARCHIVED), any(ClassArchivedEvent.class), eq(tenantPublicId));

        ClassResponse getResponse = service.get(organizationPublicId, programPublicId, classPublicId, caller);
        assertThat(getResponse.publicId()).isEqualTo(classPublicId);
    }

    @Test
    void archive_hasActiveMembers_throwsWithoutArchiving() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(classMembershipRepository.existsByStudentClass_PublicId(classPublicId)).thenReturn(true);

        assertThatThrownBy(() -> service.archive(organizationPublicId, programPublicId, classPublicId, caller))
                .isInstanceOf(ClassHasActiveMembersException.class);

        assertThat(studentClass.isDeleted()).isFalse();
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void deactivate_hasActiveMembers_throwsWithoutDeactivating() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(classMembershipRepository.existsByStudentClass_PublicId(classPublicId)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(organizationPublicId, programPublicId, classPublicId, caller))
                .isInstanceOf(ClassHasActiveMembersException.class);

        assertThat(studentClass.getStatus()).isEqualTo(ClassStatus.ACTIVE);
    }

    // --- assign ---

    @Test
    void assign_savesAndWritesOutboxEvent() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        UUID studentPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(classMembershipRepository.existsByStudentPublicId(studentPublicId)).thenReturn(false);
        when(classMembershipRepository.save(any(ClassMembership.class))).thenAnswer(invocation -> {
            ClassMembership saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        ClassMembershipResponse response = service.assign(organizationPublicId, programPublicId, classPublicId,
                new AssignStudentRequest(studentPublicId), caller);

        assertThat(response.studentPublicId()).isEqualTo(studentPublicId);
        assertThat(response.classPublicId()).isEqualTo(classPublicId);
        verify(outboxWriter).write(eq(AdminConstants.AGGREGATE_CLASS), eq(classPublicId.toString()),
                eq(AdminConstants.EVENT_STUDENT_ASSIGNED_TO_CLASS), any(StudentAssignedToClassEvent.class),
                eq(tenantPublicId));
    }

    @Test
    void assign_studentAlreadyInAnotherClass_rejectedWithoutSaving() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        UUID studentPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(classMembershipRepository.existsByStudentPublicId(studentPublicId)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(organizationPublicId, programPublicId, classPublicId,
                new AssignStudentRequest(studentPublicId), caller))
                .isInstanceOf(StudentAlreadyInClassException.class);

        verify(classMembershipRepository, never()).save(any());
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void assign_concurrentRaceOnSave_throwsAlreadyInClass() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        UUID studentPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(classMembershipRepository.existsByStudentPublicId(studentPublicId)).thenReturn(false);
        when(classMembershipRepository.save(any(ClassMembership.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> service.assign(organizationPublicId, programPublicId, classPublicId,
                new AssignStudentRequest(studentPublicId), caller))
                .isInstanceOf(StudentAlreadyInClassException.class);

        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    // --- bulkAssign ---

    @Test
    void bulkAssign_createsNewRows_reportsAlreadyInClass_writesOutboxOnlyForNewRows() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        UUID alreadyInClassId = UUID.randomUUID();
        UUID newId1 = UUID.randomUUID();
        UUID newId2 = UUID.randomUUID();

        ClassMembership existingMembership = new ClassMembership();
        existingMembership.setStudentPublicId(alreadyInClassId);

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(classMembershipRepository.findByStudentPublicIdIn(any())).thenReturn(List.of(existingMembership));
        when(classMembershipRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ClassMembership> toSave = invocation.getArgument(0);
            toSave.forEach(m -> m.setPublicId(UUID.randomUUID()));
            return toSave;
        });

        BulkAssignStudentsResponse response = service.bulkAssign(organizationPublicId, programPublicId, classPublicId,
                new BulkAssignStudentsRequest(List.of(alreadyInClassId, newId1, newId2)), caller);

        assertThat(response.assigned()).containsExactlyInAnyOrder(newId1, newId2);
        assertThat(response.alreadyInClass()).containsExactly(alreadyInClassId);
        verify(outboxWriter, times(2)).write(any(), any(), any(), any(), any());
    }

    @Test
    void bulkAssign_concurrentRaceOnSave_throwsWithoutOutbox() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        UUID studentPublicId = UUID.randomUUID();

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(classMembershipRepository.findByStudentPublicIdIn(any())).thenReturn(List.of());
        when(classMembershipRepository.saveAll(any()))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> service.bulkAssign(organizationPublicId, programPublicId, classPublicId,
                new BulkAssignStudentsRequest(List.of(studentPublicId)), caller))
                .isInstanceOf(StudentAlreadyInClassException.class);

        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    // --- unassign ---

    @Test
    void unassign_deletesAndWritesOutboxEvent() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        UUID membershipPublicId = UUID.randomUUID();
        ClassMembership membership = membershipOf(membershipPublicId, studentClass, UUID.randomUUID());
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(classMembershipRepository.findByPublicId(membershipPublicId)).thenReturn(Optional.of(membership));

        service.unassign(organizationPublicId, programPublicId, classPublicId, membershipPublicId, caller);

        verify(classMembershipRepository).delete(membership);
        verify(outboxWriter).write(eq(AdminConstants.AGGREGATE_CLASS), eq(classPublicId.toString()),
                eq(AdminConstants.EVENT_STUDENT_UNASSIGNED_FROM_CLASS), any(StudentUnassignedFromClassEvent.class),
                eq(tenantPublicId));
    }

    @Test
    void unassign_membershipFromDifferentClass_throwsNotFound_doesNotDelete() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        StudentClass otherClass = classOf(UUID.randomUUID(), program);
        UUID membershipPublicId = UUID.randomUUID();
        ClassMembership membership = membershipOf(membershipPublicId, otherClass, UUID.randomUUID());
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(classMembershipRepository.findByPublicId(membershipPublicId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.unassign(organizationPublicId, programPublicId, classPublicId,
                membershipPublicId, caller))
                .isInstanceOf(ClassMembershipNotFoundException.class);

        verify(classMembershipRepository, never()).delete(any());
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    // --- transfer ---

    @Test
    void transfer_updatesSameMembershipRowFkNotRecreated() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID sourceClassPublicId = UUID.randomUUID();
        StudentClass sourceClass = classOf(sourceClassPublicId, program);
        UUID targetClassPublicId = UUID.randomUUID();
        StudentClass targetClass = classOf(targetClassPublicId, program);
        UUID membershipPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ClassMembership membership = membershipOf(membershipPublicId, sourceClass, studentPublicId);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(sourceClassPublicId)).thenReturn(Optional.of(sourceClass));
        when(studentClassRepository.findByPublicId(targetClassPublicId)).thenReturn(Optional.of(targetClass));
        when(classMembershipRepository.findByPublicId(membershipPublicId)).thenReturn(Optional.of(membership));
        when(classMembershipRepository.save(membership)).thenReturn(membership);

        ClassMembershipResponse response = service.transfer(organizationPublicId, programPublicId, sourceClassPublicId,
                membershipPublicId, new TransferStudentRequest(targetClassPublicId), caller);

        assertThat(response.publicId()).isEqualTo(membershipPublicId);
        assertThat(response.classPublicId()).isEqualTo(targetClassPublicId);
        assertThat(membership.getStudentClass()).isEqualTo(targetClass);
        assertThat(membership.getStudentPublicId()).isEqualTo(studentPublicId);
        verify(classMembershipRepository, never()).delete(any());
        verify(classMembershipRepository).save(membership);
        verify(outboxWriter).write(eq(AdminConstants.AGGREGATE_CLASS), eq(targetClassPublicId.toString()),
                eq(AdminConstants.EVENT_STUDENT_TRANSFERRED_CLASS), any(StudentTransferredClassEvent.class),
                eq(tenantPublicId));
    }

    @Test
    void transfer_targetClassBelongsToDifferentTenant_throwsNotFoundWithoutMoving() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(callerTenantId)));
        UUID sourceClassPublicId = UUID.randomUUID();
        StudentClass sourceClass = classOf(sourceClassPublicId, program);
        UUID targetClassPublicId = UUID.randomUUID();
        Program otherTenantProgram = programOf(UUID.randomUUID(),
                organizationOf(UUID.randomUUID(), tenantWithPublicId(otherTenantId)));
        StudentClass targetClass = classOf(targetClassPublicId, otherTenantProgram);
        UUID membershipPublicId = UUID.randomUUID();
        ClassMembership membership = membershipOf(membershipPublicId, sourceClass, UUID.randomUUID());
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(sourceClassPublicId)).thenReturn(Optional.of(sourceClass));
        when(studentClassRepository.findByPublicId(targetClassPublicId)).thenReturn(Optional.of(targetClass));
        when(classMembershipRepository.findByPublicId(membershipPublicId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.transfer(organizationPublicId, programPublicId, sourceClassPublicId,
                membershipPublicId, new TransferStudentRequest(targetClassPublicId), caller))
                .isInstanceOf(StudentClassNotFoundException.class);

        assertThat(membership.getStudentClass()).isEqualTo(sourceClass);
        verify(classMembershipRepository, never()).save(any());
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void transfer_toDifferentProgramOfSameTenant_succeeds() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = organizationOf(organizationPublicId, tenant);
        UUID sourceProgramPublicId = UUID.randomUUID();
        Program sourceProgram = programOf(sourceProgramPublicId, organization);
        UUID targetProgramPublicId = UUID.randomUUID();
        Program targetProgram = programOf(targetProgramPublicId, organization);
        UUID sourceClassPublicId = UUID.randomUUID();
        StudentClass sourceClass = classOf(sourceClassPublicId, sourceProgram);
        UUID targetClassPublicId = UUID.randomUUID();
        StudentClass targetClass = classOf(targetClassPublicId, targetProgram);
        UUID membershipPublicId = UUID.randomUUID();
        ClassMembership membership = membershipOf(membershipPublicId, sourceClass, UUID.randomUUID());
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(sourceClassPublicId)).thenReturn(Optional.of(sourceClass));
        when(studentClassRepository.findByPublicId(targetClassPublicId)).thenReturn(Optional.of(targetClass));
        when(classMembershipRepository.findByPublicId(membershipPublicId)).thenReturn(Optional.of(membership));
        when(classMembershipRepository.save(membership)).thenReturn(membership);

        ClassMembershipResponse response = service.transfer(organizationPublicId, sourceProgramPublicId,
                sourceClassPublicId, membershipPublicId, new TransferStudentRequest(targetClassPublicId), caller);

        assertThat(response.programPublicId()).isEqualTo(targetProgramPublicId);
        assertThat(response.classPublicId()).isEqualTo(targetClassPublicId);
    }

    // --- listMemberships ---

    @Test
    void listMemberships_noProgramFilter_forwardsCallerTenantOnly() {
        UUID tenantPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(classMembershipRepository.findByStudentClass_Program_Organization_Tenant_PublicId(tenantPublicId))
                .thenReturn(List.of());

        List<ClassMembershipResponse> result = service.listMemberships(null, caller);

        assertThat(result).isEmpty();
        verify(classMembershipRepository).findByStudentClass_Program_Organization_Tenant_PublicId(tenantPublicId);
        verify(classMembershipRepository, never())
                .findByStudentClass_Program_Organization_Tenant_PublicIdAndStudentClass_Program_PublicId(any(), any());
    }

    @Test
    void listMemberships_withProgramFilter_forwardsBothTenantAndProgram() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(classMembershipRepository
                .findByStudentClass_Program_Organization_Tenant_PublicIdAndStudentClass_Program_PublicId(
                        tenantPublicId, programPublicId))
                .thenReturn(List.of());

        List<ClassMembershipResponse> result = service.listMemberships(programPublicId, caller);

        assertThat(result).isEmpty();
        verify(classMembershipRepository)
                .findByStudentClass_Program_Organization_Tenant_PublicIdAndStudentClass_Program_PublicId(
                        tenantPublicId, programPublicId);
    }
}
