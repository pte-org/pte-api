package com.pte.identity;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.repository.LoginHashRepository;
import com.pte.identity.internal.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginHashRepository loginHashRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private IdentityService service;

    @BeforeEach
    void setUp() {
        service = new IdentityService(userRepository, loginHashRepository, passwordEncoder);
    }

    @Test
    void findByTenantIdAndRole_delegatesToRepository() {
        UUID tenantId = UUID.randomUUID();

        User hostAdmin1 = new User();
        hostAdmin1.setPublicId(UUID.randomUUID());
        hostAdmin1.setEmail("admin1@example.com");

        User hostAdmin2 = new User();
        hostAdmin2.setPublicId(UUID.randomUUID());
        hostAdmin2.setEmail("admin2@example.com");

        when(userRepository.findByTenantIdAndRolesContaining(tenantId, Role.HOST_ADMIN))
                .thenReturn(List.of(hostAdmin1, hostAdmin2));

        List<User> result = service.findByTenantIdAndRole(tenantId, Role.HOST_ADMIN);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(hostAdmin1, hostAdmin2);
        verify(userRepository).findByTenantIdAndRolesContaining(tenantId, Role.HOST_ADMIN);
    }

    @Test
    void findByTenantIdAndRole_emptyResult() {
        UUID tenantId = UUID.randomUUID();

        when(userRepository.findByTenantIdAndRolesContaining(tenantId, Role.HOST_ADMIN))
                .thenReturn(List.of());

        List<User> result = service.findByTenantIdAndRole(tenantId, Role.HOST_ADMIN);

        assertThat(result).isEmpty();
        verify(userRepository).findByTenantIdAndRolesContaining(tenantId, Role.HOST_ADMIN);
    }

    @Test
    void findByTenantIdAndRole_multipleRoles() {
        UUID tenantId = UUID.randomUUID();

        User student1 = new User();
        student1.setPublicId(UUID.randomUUID());
        student1.setEmail("student1@example.com");

        User student2 = new User();
        student2.setPublicId(UUID.randomUUID());
        student2.setEmail("student2@example.com");

        when(userRepository.findByTenantIdAndRolesContaining(tenantId, Role.STUDENT))
                .thenReturn(List.of(student1, student2));

        List<User> result = service.findByTenantIdAndRole(tenantId, Role.STUDENT);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(student1, student2);
    }

    @Test
    void findActiveStudentInTenant_requiresTenantRoleActiveStatusAndNotDeleted() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        User student = new User();
        student.setPublicId(studentId);
        student.setTenantId(tenantId);
        student.setRoles(java.util.Set.of(Role.STUDENT));

        when(userRepository.findByPublicIdAndTenantId(studentId, tenantId)).thenReturn(java.util.Optional.of(student));

        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).contains(student);

        student.setStatus(UserStatus.SUSPENDED);
        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).isEmpty();

        student.setStatus(UserStatus.ACTIVE);
        student.setDeleted(true);
        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).isEmpty();

        student.setDeleted(false);
        student.setRoles(java.util.Set.of(Role.PROCTOR));
        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).isEmpty();
    }

    @Test
    void requireActiveStudentsInTenant_failsClosedForMixedEligibility() {
        UUID tenantId = UUID.randomUUID();
        UUID activeStudentId = UUID.randomUUID();
        UUID suspendedStudentId = UUID.randomUUID();
        User activeStudent = new User();
        activeStudent.setPublicId(activeStudentId);
        activeStudent.setTenantId(tenantId);
        activeStudent.setRoles(java.util.Set.of(Role.STUDENT));
        User suspendedStudent = new User();
        suspendedStudent.setPublicId(suspendedStudentId);
        suspendedStudent.setTenantId(tenantId);
        suspendedStudent.setStatus(UserStatus.SUSPENDED);
        suspendedStudent.setRoles(java.util.Set.of(Role.STUDENT));
        when(userRepository.findByPublicIdInAndTenantIdAndDeletedFalse(
                java.util.List.of(activeStudentId, suspendedStudentId), tenantId))
                .thenReturn(java.util.List.of(activeStudent, suspendedStudent));

        assertThatThrownBy(() -> service.requireActiveStudentsInTenant(
                java.util.List.of(activeStudentId, suspendedStudentId), tenantId))
                .isInstanceOf(IdentityService.IdentityNotEligibleException.class);
    }

    @Test
    void lockActiveStudentsInTenant_sortsIdsAndLocksOnlyEligibleStudents() {
        UUID tenantId = UUID.randomUUID();
        UUID firstStudentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondStudentId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        User firstStudent = activeStudent(firstStudentId, tenantId);
        User secondStudent = activeStudent(secondStudentId, tenantId);
        when(userRepository.findWithLockByPublicIdsAndTenantId(
                List.of(firstStudentId, secondStudentId), tenantId))
                .thenReturn(List.of(secondStudent, firstStudent));

        service.lockActiveStudentsInTenant(List.of(secondStudentId, firstStudentId, firstStudentId), tenantId);

        verify(userRepository).findWithLockByPublicIdsAndTenantId(
                List.of(firstStudentId, secondStudentId), tenantId);
    }

    @Test
    void lockActiveStudentsInTenant_failsClosedWhenLockedRowsAreNotEligible() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        User suspendedStudent = activeStudent(studentId, tenantId);
        suspendedStudent.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findWithLockByPublicIdsAndTenantId(List.of(studentId), tenantId))
                .thenReturn(List.of(suspendedStudent));

        assertThatThrownBy(() -> service.lockActiveStudentsInTenant(List.of(studentId), tenantId))
                .isInstanceOf(IdentityService.IdentityNotEligibleException.class);
    }

    @Test
    void countStudents_delegatesToRoleAwareRepositoryQuery() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.countByTenantIdAndRole(tenantId, Role.STUDENT)).thenReturn(487L);

        assertThat(service.countStudents(tenantId)).isEqualTo(487L);

        verify(userRepository).countByTenantIdAndRole(tenantId, Role.STUDENT);
    }

    private User activeStudent(UUID publicId, UUID tenantId) {
        User student = new User();
        student.setPublicId(publicId);
        student.setTenantId(tenantId);
        student.setStatus(UserStatus.ACTIVE);
        student.setRoles(java.util.Set.of(Role.STUDENT));
        return student;
    }
}
