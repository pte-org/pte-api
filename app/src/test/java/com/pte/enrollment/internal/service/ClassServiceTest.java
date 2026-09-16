package com.pte.enrollment.internal.service;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.enrollment.domain.ClassMembership;
import com.pte.tenancy.domain.Organization;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.StudentClass;
import com.pte.tenancy.domain.Tenant;
import com.pte.enrollment.domain.enums.ClassStatus;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.enrollment.internal.exception.ClassHasActiveMembersException;
import com.pte.enrollment.internal.exception.ClassMembershipNotFoundException;
import com.pte.enrollment.internal.exception.ProgramNotFoundException;
import com.pte.enrollment.internal.exception.StudentAlreadyInClassException;
import com.pte.enrollment.internal.exception.StudentClassNameAlreadyUsedException;
import com.pte.enrollment.internal.exception.StudentClassNotFoundException;
import com.pte.enrollment.internal.dto.request.AssignStudentRequest;
import com.pte.enrollment.internal.dto.request.BulkAssignStudentsRequest;
import com.pte.enrollment.internal.dto.request.CreateClassRequest;
import com.pte.enrollment.internal.dto.request.MergeClassesRequest;
import com.pte.enrollment.internal.dto.request.SplitClassRequest;
import com.pte.enrollment.internal.dto.request.TransferStudentRequest;
import com.pte.enrollment.internal.dto.request.UpdateClassRequest;
import com.pte.enrollment.internal.dto.response.BulkAssignStudentsResponse;
import com.pte.enrollment.internal.dto.response.ClassMembershipResponse;
import com.pte.enrollment.internal.dto.response.ClassResponse;
import com.pte.enrollment.internal.dto.response.MergeClassesResponse;
import com.pte.enrollment.internal.dto.response.SplitClassResponse;
import com.pte.enrollment.internal.repository.ClassMembershipRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.StudentClassRepository;
import com.pte.identity.IdentityService;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
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
import static org.mockito.Mockito.lenient;
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
        private IdentityService identityService;

        @Mock
        private AuditLogService auditLogService;

        private ClassService service;

        @BeforeEach
        void setUp() {
                service = new ClassService(studentClassRepository, classMembershipRepository, programRepository,
                                identityService, auditLogService);
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
        void create_savesClassUnderProgram() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
                when(studentClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(programPublicId,
                                "12A1"))
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
                verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS), any(),
                                eq(EnrollmentConstants.EVENT_CLASS_CREATED), any());
        }

        @Test
        void create_programBelongsToDifferentTenant_throwsNotFoundWithoutSaving() {
                UUID callerTenantId = UUID.randomUUID();
                UUID otherTenantId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(otherTenantId)));
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(callerTenantId));

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
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
                when(studentClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(programPublicId,
                                "12A1"))
                                .thenReturn(true);

                assertThatThrownBy(() -> service.create(organizationPublicId, programPublicId,
                                new CreateClassRequest("12A1"), caller))
                                .isInstanceOf(StudentClassNameAlreadyUsedException.class);
                verify(studentClassRepository, never()).save(any());
        }

        // --- get / tenant-mismatch ---

        @Test
        void get_classBelongsToDifferentTenant_throwsNotFound() {
                UUID callerTenantId = UUID.randomUUID();
                UUID otherTenantId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                UUID classPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(otherTenantId)));
                StudentClass studentClass = classOf(classPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(callerTenantId));

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
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));

                assertThatThrownBy(() -> service.get(organization.getPublicId(), requestedProgramPublicId,
                                classPublicId, caller))
                                .isInstanceOf(StudentClassNotFoundException.class);
        }

        // --- update ---

        @Test
        void update_changesName() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));

                ClassResponse response = service.update(organizationPublicId, programPublicId, classPublicId,
                                new UpdateClassRequest("12A2"), caller);

                assertThat(response.name()).isEqualTo("12A2");
                verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS),
                                eq(classPublicId.toString()),
                                eq(EnrollmentConstants.EVENT_CLASS_UPDATED), any());
        }

        // --- status/archive ---

        @Test
        void suspend_activeClass_transitions() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));

                ClassResponse response = service.suspend(organizationPublicId, programPublicId, classPublicId, caller);

                assertThat(response.status()).isEqualTo("SUSPENDED");
                verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS),
                                eq(classPublicId.toString()),
                                eq(EnrollmentConstants.EVENT_CLASS_STATUS_CHANGED), any());
        }

        @Test
        void archive_activeClassWithNoMembers_marksDeletedButStaysFetchable() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
                when(classMembershipRepository.existsByTenantIdAndStudentClass_PublicId(caller.tenantId(),
                                classPublicId)).thenReturn(false);

                ClassResponse response = service.archive(organizationPublicId, programPublicId, classPublicId, caller);

                assertThat(studentClass.isDeleted()).isTrue();
                assertThat(response.publicId()).isEqualTo(classPublicId);
                verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS),
                                eq(classPublicId.toString()),
                                eq(EnrollmentConstants.EVENT_CLASS_ARCHIVED), any());

                ClassResponse getResponse = service.get(organizationPublicId, programPublicId, classPublicId, caller);
                assertThat(getResponse.publicId()).isEqualTo(classPublicId);
        }

        @Test
        void archive_hasActiveMembers_throwsWithoutArchiving() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
                when(classMembershipRepository.existsByTenantIdAndStudentClass_PublicId(caller.tenantId(),
                                classPublicId)).thenReturn(true);

                assertThatThrownBy(() -> service.archive(organizationPublicId, programPublicId, classPublicId, caller))
                                .isInstanceOf(ClassHasActiveMembersException.class);

                assertThat(studentClass.isDeleted()).isFalse();
        }

        @Test
        void deactivate_hasActiveMembers_throwsWithoutDeactivating() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
                when(classMembershipRepository.existsByTenantIdAndStudentClass_PublicId(caller.tenantId(),
                                classPublicId)).thenReturn(true);

                assertThatThrownBy(
                                () -> service.deactivate(organizationPublicId, programPublicId, classPublicId, caller))
                                .isInstanceOf(ClassHasActiveMembersException.class);

                assertThat(studentClass.getStatus()).isEqualTo(ClassStatus.ACTIVE);
        }

        // --- assign ---

        @Test
        void assign_saves() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                UUID studentPublicId = UUID.randomUUID();
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
                when(classMembershipRepository.existsByStudentPublicIdAndTenantId(studentPublicId, caller.tenantId()))
                                .thenReturn(false);
                when(classMembershipRepository.save(any(ClassMembership.class))).thenAnswer(invocation -> {
                        ClassMembership saved = invocation.getArgument(0);
                        saved.setPublicId(UUID.randomUUID());
                        return saved;
                });

                ClassMembershipResponse response = service.assign(organizationPublicId, programPublicId, classPublicId,
                                new AssignStudentRequest(studentPublicId), caller);

                assertThat(response.studentPublicId()).isEqualTo(studentPublicId);
                assertThat(response.classPublicId()).isEqualTo(classPublicId);
                verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS),
                                eq(classPublicId.toString()),
                                eq(EnrollmentConstants.EVENT_STUDENT_ASSIGNED_TO_CLASS), any());
        }

        @Test
        void assign_studentAlreadyInAnotherClass_rejectedWithoutSaving() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                UUID studentPublicId = UUID.randomUUID();
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
                when(classMembershipRepository.existsByStudentPublicIdAndTenantId(studentPublicId, caller.tenantId()))
                                .thenReturn(true);

                assertThatThrownBy(() -> service.assign(organizationPublicId, programPublicId, classPublicId,
                                new AssignStudentRequest(studentPublicId), caller))
                                .isInstanceOf(StudentAlreadyInClassException.class);

                verify(classMembershipRepository, never()).save(any());
        }

        @Test
        void assign_concurrentRaceOnSave_throwsAlreadyInClass() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                UUID studentPublicId = UUID.randomUUID();
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
                when(classMembershipRepository.existsByStudentPublicIdAndTenantId(studentPublicId, caller.tenantId()))
                                .thenReturn(false);
                when(classMembershipRepository.save(any(ClassMembership.class)))
                                .thenThrow(new DataIntegrityViolationException("unique violation"));

                assertThatThrownBy(() -> service.assign(organizationPublicId, programPublicId, classPublicId,
                                new AssignStudentRequest(studentPublicId), caller))
                                .isInstanceOf(StudentAlreadyInClassException.class);
        }

        // --- bulkAssign ---

        @Test
        void bulkAssign_createsNewRows_reportsAlreadyInClass_writesAuditOnlyForNewRows() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                UUID alreadyInClassId = UUID.randomUUID();
                UUID newId1 = UUID.randomUUID();
                UUID newId2 = UUID.randomUUID();

                ClassMembership existingMembership = new ClassMembership();
                existingMembership.setStudentPublicId(alreadyInClassId);

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
                when(classMembershipRepository.findByTenantIdAndStudentPublicIdIn(eq(caller.tenantId()), any()))
                                .thenReturn(List.of(existingMembership));
                when(classMembershipRepository.saveAll(any())).thenAnswer(invocation -> {
                        List<ClassMembership> toSave = invocation.getArgument(0);
                        toSave.forEach(m -> m.setPublicId(UUID.randomUUID()));
                        return toSave;
                });

                BulkAssignStudentsResponse response = service.bulkAssign(organizationPublicId, programPublicId,
                                classPublicId,
                                new BulkAssignStudentsRequest(List.of(alreadyInClassId, newId1, newId2)), caller);

                assertThat(response.assigned()).containsExactlyInAnyOrder(newId1, newId2);
                assertThat(response.alreadyInClass()).containsExactly(alreadyInClassId);
                verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS),
                                eq(classPublicId.toString()),
                                eq(EnrollmentConstants.EVENT_STUDENT_ASSIGNED_TO_CLASS), any());
        }

        @Test
        void bulkAssign_concurrentRaceOnSave_throwsWithoutExtraAudit() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));
                UUID studentPublicId = UUID.randomUUID();

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
                when(classMembershipRepository.findByTenantIdAndStudentPublicIdIn(eq(caller.tenantId()), any()))
                                .thenReturn(List.of());
                when(classMembershipRepository.saveAll(any()))
                                .thenThrow(new DataIntegrityViolationException("unique violation"));

                assertThatThrownBy(() -> service.bulkAssign(organizationPublicId, programPublicId, classPublicId,
                                new BulkAssignStudentsRequest(List.of(studentPublicId)), caller))
                                .isInstanceOf(StudentAlreadyInClassException.class);
        }

        // --- unassign ---

        @Test
        void unassign_deletes() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                UUID membershipPublicId = UUID.randomUUID();
                ClassMembership membership = membershipOf(membershipPublicId, studentClass, UUID.randomUUID());
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
                when(classMembershipRepository.findByPublicIdAndTenantId(membershipPublicId, caller.tenantId()))
                                .thenReturn(Optional.of(membership));

                service.unassign(organizationPublicId, programPublicId, classPublicId, membershipPublicId, caller);

                verify(classMembershipRepository).delete(membership);
                verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS),
                                eq(classPublicId.toString()),
                                eq(EnrollmentConstants.EVENT_STUDENT_UNASSIGNED_FROM_CLASS), any());
        }

        @Test
        void unassign_membershipFromDifferentClass_throwsNotFound_doesNotDelete() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID classPublicId = UUID.randomUUID();
                StudentClass studentClass = classOf(classPublicId, program);
                StudentClass otherClass = classOf(UUID.randomUUID(), program);
                UUID membershipPublicId = UUID.randomUUID();
                ClassMembership membership = membershipOf(membershipPublicId, otherClass, UUID.randomUUID());
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
                when(classMembershipRepository.findByPublicIdAndTenantId(membershipPublicId, caller.tenantId()))
                                .thenReturn(Optional.of(membership));

                assertThatThrownBy(() -> service.unassign(organizationPublicId, programPublicId, classPublicId,
                                membershipPublicId, caller))
                                .isInstanceOf(ClassMembershipNotFoundException.class);

                verify(classMembershipRepository, never()).delete(any());
        }

        // --- transfer ---

        @Test
        void transfer_updatesSameMembershipRowFkNotRecreated() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID sourceClassPublicId = UUID.randomUUID();
                StudentClass sourceClass = classOf(sourceClassPublicId, program);
                UUID targetClassPublicId = UUID.randomUUID();
                StudentClass targetClass = classOf(targetClassPublicId, program);
                UUID membershipPublicId = UUID.randomUUID();
                UUID studentPublicId = UUID.randomUUID();
                ClassMembership membership = membershipOf(membershipPublicId, sourceClass, studentPublicId);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(sourceClassPublicId)).thenReturn(Optional.of(sourceClass));
                when(studentClassRepository.findByPublicId(targetClassPublicId)).thenReturn(Optional.of(targetClass));
                when(classMembershipRepository.findByPublicIdAndTenantId(membershipPublicId, caller.tenantId()))
                                .thenReturn(Optional.of(membership));
                when(classMembershipRepository.save(membership)).thenReturn(membership);

                ClassMembershipResponse response = service.transfer(organizationPublicId, programPublicId,
                                sourceClassPublicId,
                                membershipPublicId, new TransferStudentRequest(targetClassPublicId), caller);

                assertThat(response.publicId()).isEqualTo(membershipPublicId);
                assertThat(response.classPublicId()).isEqualTo(targetClassPublicId);
                assertThat(membership.getStudentClass()).isEqualTo(targetClass);
                assertThat(membership.getStudentPublicId()).isEqualTo(studentPublicId);
                verify(classMembershipRepository, never()).delete(any());
                verify(classMembershipRepository).save(membership);
                verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS),
                                eq(targetClassPublicId.toString()),
                                eq(EnrollmentConstants.EVENT_STUDENT_TRANSFERRED_CLASS), any());
        }

        @Test
        void transfer_targetClassBelongsToDifferentTenant_throwsNotFoundWithoutMoving() {
                UUID callerTenantId = UUID.randomUUID();
                UUID otherTenantId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(callerTenantId)));
                UUID sourceClassPublicId = UUID.randomUUID();
                StudentClass sourceClass = classOf(sourceClassPublicId, program);
                UUID targetClassPublicId = UUID.randomUUID();
                Program otherTenantProgram = programOf(UUID.randomUUID(),
                                organizationOf(UUID.randomUUID(), tenantWithPublicId(otherTenantId)));
                StudentClass targetClass = classOf(targetClassPublicId, otherTenantProgram);
                UUID membershipPublicId = UUID.randomUUID();
                ClassMembership membership = membershipOf(membershipPublicId, sourceClass, UUID.randomUUID());
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(callerTenantId));

                when(studentClassRepository.findByPublicId(sourceClassPublicId)).thenReturn(Optional.of(sourceClass));
                when(studentClassRepository.findByPublicId(targetClassPublicId)).thenReturn(Optional.of(targetClass));
                when(classMembershipRepository.findByPublicIdAndTenantId(membershipPublicId, caller.tenantId()))
                                .thenReturn(Optional.of(membership));

                assertThatThrownBy(() -> service.transfer(organizationPublicId, programPublicId, sourceClassPublicId,
                                membershipPublicId, new TransferStudentRequest(targetClassPublicId), caller))
                                .isInstanceOf(StudentClassNotFoundException.class);

                assertThat(membership.getStudentClass()).isEqualTo(sourceClass);
                verify(classMembershipRepository, never()).save(any());
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
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(sourceClassPublicId)).thenReturn(Optional.of(sourceClass));
                when(studentClassRepository.findByPublicId(targetClassPublicId)).thenReturn(Optional.of(targetClass));
                when(classMembershipRepository.findByPublicIdAndTenantId(membershipPublicId, caller.tenantId()))
                                .thenReturn(Optional.of(membership));
                when(classMembershipRepository.save(membership)).thenReturn(membership);

                ClassMembershipResponse response = service.transfer(organizationPublicId, sourceProgramPublicId,
                                sourceClassPublicId, membershipPublicId,
                                new TransferStudentRequest(targetClassPublicId), caller);

                assertThat(response.programPublicId()).isEqualTo(targetProgramPublicId);
                assertThat(response.classPublicId()).isEqualTo(targetClassPublicId);
        }

        // --- mergeClasses ---

        @Test
        void mergeClasses_movesEveryStudentFromSourcesToTarget_writesAuditSummary() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID targetClassPublicId = UUID.randomUUID();
                StudentClass targetClass = classOf(targetClassPublicId, program);
                UUID sourceClassAPublicId = UUID.randomUUID();
                StudentClass sourceClassA = classOf(sourceClassAPublicId, program);
                UUID sourceClassBPublicId = UUID.randomUUID();
                StudentClass sourceClassB = classOf(sourceClassBPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                UUID studentA1 = UUID.randomUUID();
                UUID studentA2 = UUID.randomUUID();
                UUID studentB1 = UUID.randomUUID();
                ClassMembership membershipA1 = membershipOf(UUID.randomUUID(), sourceClassA, studentA1);
                ClassMembership membershipA2 = membershipOf(UUID.randomUUID(), sourceClassA, studentA2);
                ClassMembership membershipB1 = membershipOf(UUID.randomUUID(), sourceClassB, studentB1);

                when(studentClassRepository.findByPublicId(targetClassPublicId)).thenReturn(Optional.of(targetClass));
                when(studentClassRepository.findByPublicId(sourceClassAPublicId)).thenReturn(Optional.of(sourceClassA));
                when(studentClassRepository.findByPublicId(sourceClassBPublicId)).thenReturn(Optional.of(sourceClassB));
                when(classMembershipRepository.findByTenantIdAndStudentClass_PublicId(caller.tenantId(),
                                sourceClassAPublicId))
                                .thenReturn(List.of(membershipA1, membershipA2));
                when(classMembershipRepository.findByTenantIdAndStudentClass_PublicId(caller.tenantId(),
                                sourceClassBPublicId))
                                .thenReturn(List.of(membershipB1));
                when(classMembershipRepository.save(any(ClassMembership.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                MergeClassesResponse response = service.mergeClasses(organizationPublicId, programPublicId,
                                targetClassPublicId,
                                new MergeClassesRequest(List.of(sourceClassAPublicId, sourceClassBPublicId)), caller);

                assertThat(response.targetClassPublicId()).isEqualTo(targetClassPublicId);
                assertThat(response.movedStudentPublicIds()).containsExactlyInAnyOrder(studentA1, studentA2, studentB1);
                assertThat(membershipA1.getStudentClass()).isEqualTo(targetClass);
                assertThat(membershipA2.getStudentClass()).isEqualTo(targetClass);
                assertThat(membershipB1.getStudentClass()).isEqualTo(targetClass);
                verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS),
                                eq(targetClassPublicId.toString()),
                                eq(EnrollmentConstants.EVENT_CLASSES_MERGED), any());
        }

        @Test
        void mergeClasses_sourceBelongsToDifferentTenant_throwsWithoutMovingAnyMembership() {
                UUID callerTenantId = UUID.randomUUID();
                UUID otherTenantId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(callerTenantId)));
                UUID targetClassPublicId = UUID.randomUUID();
                StudentClass targetClass = classOf(targetClassPublicId, program);
                UUID sourceClassPublicId = UUID.randomUUID();
                Program otherTenantProgram = programOf(UUID.randomUUID(),
                                organizationOf(UUID.randomUUID(), tenantWithPublicId(otherTenantId)));
                StudentClass sourceClass = classOf(sourceClassPublicId, otherTenantProgram);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(callerTenantId));

                when(studentClassRepository.findByPublicId(targetClassPublicId)).thenReturn(Optional.of(targetClass));
                when(studentClassRepository.findByPublicId(sourceClassPublicId)).thenReturn(Optional.of(sourceClass));

                assertThatThrownBy(
                                () -> service.mergeClasses(organizationPublicId, programPublicId, targetClassPublicId,
                                                new MergeClassesRequest(List.of(sourceClassPublicId)), caller))
                                .isInstanceOf(StudentClassNotFoundException.class);

                verify(classMembershipRepository, never()).findByTenantIdAndStudentClass_PublicId(any(), any());
                verify(classMembershipRepository, never()).save(any());
        }

        @Test
        void mergeClasses_targetBelongsToDifferentTenant_throwsWithoutMoving() {
                UUID callerTenantId = UUID.randomUUID();
                UUID otherTenantId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program otherTenantProgram = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(otherTenantId)));
                UUID targetClassPublicId = UUID.randomUUID();
                StudentClass targetClass = classOf(targetClassPublicId, otherTenantProgram);
                UUID sourceClassPublicId = UUID.randomUUID();
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(callerTenantId));

                when(studentClassRepository.findByPublicId(targetClassPublicId)).thenReturn(Optional.of(targetClass));

                assertThatThrownBy(
                                () -> service.mergeClasses(organizationPublicId, programPublicId, targetClassPublicId,
                                                new MergeClassesRequest(List.of(sourceClassPublicId)), caller))
                                .isInstanceOf(StudentClassNotFoundException.class);

                verify(classMembershipRepository, never()).save(any());
        }

        @Test
        void mergeClasses_sourceEqualsTarget_skippedAsNoOp() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID targetClassPublicId = UUID.randomUUID();
                StudentClass targetClass = classOf(targetClassPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(targetClassPublicId)).thenReturn(Optional.of(targetClass));

                MergeClassesResponse response = service.mergeClasses(organizationPublicId, programPublicId,
                                targetClassPublicId,
                                new MergeClassesRequest(List.of(targetClassPublicId)), caller);

                assertThat(response.movedStudentPublicIds()).isEmpty();
                verify(classMembershipRepository, never()).findByTenantIdAndStudentClass_PublicId(any(), any());
                // The audit log skips a "0 student(s) moved" row for a true no-op —
                // same discipline as bulkAssign.
                verify(auditLogService, never()).record(any(), any(), any(), any(), any());
        }

        // --- splitClass ---

        @Test
        void splitClass_createsNewClassAndMovesOnlySelectedSubset() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID sourceClassPublicId = UUID.randomUUID();
                StudentClass sourceClass = classOf(sourceClassPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                UUID studentToMove1 = UUID.randomUUID();
                UUID studentToMove2 = UUID.randomUUID();
                ClassMembership membershipToMove1 = membershipOf(UUID.randomUUID(), sourceClass, studentToMove1);
                ClassMembership membershipToMove2 = membershipOf(UUID.randomUUID(), sourceClass, studentToMove2);

                when(studentClassRepository.findByPublicId(sourceClassPublicId)).thenReturn(Optional.of(sourceClass));
                when(studentClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(programPublicId,
                                "12A2"))
                                .thenReturn(false);
                when(studentClassRepository.save(any(StudentClass.class))).thenAnswer(invocation -> {
                        StudentClass saved = invocation.getArgument(0);
                        saved.setPublicId(UUID.randomUUID());
                        return saved;
                });
                when(classMembershipRepository.findByTenantIdAndStudentClass_PublicIdAndStudentPublicIdIn(
                                eq(caller.tenantId()),
                                eq(sourceClassPublicId), eq(List.of(studentToMove1, studentToMove2))))
                                .thenReturn(List.of(membershipToMove1, membershipToMove2));
                when(classMembershipRepository.save(any(ClassMembership.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                SplitClassResponse response = service.splitClass(organizationPublicId, programPublicId,
                                sourceClassPublicId,
                                new SplitClassRequest("12A2", List.of(studentToMove1, studentToMove2)), caller);

                assertThat(response.newClass().name()).isEqualTo("12A2");
                assertThat(response.newClass().programPublicId()).isEqualTo(programPublicId);
                assertThat(response.movedStudentPublicIds()).containsExactlyInAnyOrder(studentToMove1, studentToMove2);
                assertThat(membershipToMove1.getStudentClass().getName()).isEqualTo("12A2");
                assertThat(membershipToMove2.getStudentClass().getName()).isEqualTo("12A2");
                verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS), any(),
                                eq(EnrollmentConstants.EVENT_CLASS_SPLIT), any());
        }

        @Test
        void splitClass_duplicateNameInProgram_throwsWithoutCreatingOrMoving() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
                UUID sourceClassPublicId = UUID.randomUUID();
                StudentClass sourceClass = classOf(sourceClassPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(studentClassRepository.findByPublicId(sourceClassPublicId)).thenReturn(Optional.of(sourceClass));
                when(studentClassRepository.existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(programPublicId,
                                "12A1"))
                                .thenReturn(true);

                assertThatThrownBy(() -> service.splitClass(organizationPublicId, programPublicId, sourceClassPublicId,
                                new SplitClassRequest("12A1", List.of(UUID.randomUUID())), caller))
                                .isInstanceOf(StudentClassNameAlreadyUsedException.class);

                verify(studentClassRepository, never()).save(any());
                verify(classMembershipRepository, never())
                                .findByTenantIdAndStudentClass_PublicIdAndStudentPublicIdIn(any(), any(), any());
        }

        @Test
        void splitClass_sourceBelongsToDifferentTenant_throwsWithoutCreating() {
                UUID callerTenantId = UUID.randomUUID();
                UUID otherTenantId = UUID.randomUUID();
                UUID organizationPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                Program program = programOf(programPublicId,
                                organizationOf(organizationPublicId, tenantWithPublicId(otherTenantId)));
                UUID sourceClassPublicId = UUID.randomUUID();
                StudentClass sourceClass = classOf(sourceClassPublicId, program);
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(callerTenantId));

                when(studentClassRepository.findByPublicId(sourceClassPublicId)).thenReturn(Optional.of(sourceClass));

                assertThatThrownBy(() -> service.splitClass(organizationPublicId, programPublicId, sourceClassPublicId,
                                new SplitClassRequest("12A2", List.of(UUID.randomUUID())), caller))
                                .isInstanceOf(StudentClassNotFoundException.class);

                verify(studentClassRepository, never()).save(any());
        }

        // --- listMemberships ---

        @Test
        void listMemberships_noProgramFilter_forwardsCallerTenantOnly() {
                UUID tenantPublicId = UUID.randomUUID();
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

                when(classMembershipRepository.findByStudentClass_Program_Organization_Tenant_PublicId(tenantPublicId))
                                .thenReturn(List.of());

                List<ClassMembershipResponse> result = service.listMemberships(null, caller);

                assertThat(result).isEmpty();
                verify(classMembershipRepository)
                                .findByStudentClass_Program_Organization_Tenant_PublicId(tenantPublicId);
                verify(classMembershipRepository, never())
                                .findByStudentClass_Program_Organization_Tenant_PublicIdAndStudentClass_Program_PublicId(
                                                any(), any());
        }

        @Test
        void listMemberships_withProgramFilter_forwardsBothTenantAndProgram() {
                UUID tenantPublicId = UUID.randomUUID();
                UUID programPublicId = UUID.randomUUID();
                CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
                lenient().when(identityService.getTenantOf(any())).thenReturn(Optional.of(tenantPublicId));

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
