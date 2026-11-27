package com.pte.identity;

import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private IdentityService service;

    @BeforeEach
    void setUp() {
        service = new IdentityService(userRepository, passwordEncoder);
    }

    private User account(UUID publicId, UUID tenantId, Role role) {
        User user = new User();
        user.setPublicId(publicId);
        user.setStatus(UserStatus.ACTIVE);
        user.setOrganizationId(tenantId);
        user.setRoles(new HashSet<>(Set.of(role)));
        return user;
    }

    @Test
    void findByTenantIdAndRole_returnsTheAccountsOfTheOrganization() {
        UUID tenantId = UUID.randomUUID();
        User first = account(UUID.randomUUID(), tenantId, Role.HOST_ADMIN);
        User second = account(UUID.randomUUID(), tenantId, Role.HOST_ADMIN);
        when(userRepository.findActiveByOrganizationAndRole(tenantId, Role.HOST_ADMIN))
                .thenReturn(List.of(first, second));

        List<User> result = service.findByTenantIdAndRole(tenantId, Role.HOST_ADMIN);

        assertThat(result).containsExactly(first, second);
        verify(userRepository).findActiveByOrganizationAndRole(tenantId, Role.HOST_ADMIN);
    }

    @Test
    void findByTenantIdAndRole_emptyResult() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.findActiveByOrganizationAndRole(tenantId, Role.HOST_ADMIN)).thenReturn(List.of());

        assertThat(service.findByTenantIdAndRole(tenantId, Role.HOST_ADMIN)).isEmpty();
    }

    @Test
    void findActiveByTenantIdAndRole_dropsSuspendedAccounts() {
        UUID tenantId = UUID.randomUUID();
        User active = account(UUID.randomUUID(), tenantId, Role.HOST_ADMIN);
        User suspended = account(UUID.randomUUID(), tenantId, Role.HOST_ADMIN);
        suspended.suspend();
        when(userRepository.findActiveByOrganizationAndRole(tenantId, Role.HOST_ADMIN))
                .thenReturn(List.of(active, suspended));

        assertThat(service.findActiveByTenantIdAndRole(tenantId, Role.HOST_ADMIN)).containsExactly(active);
    }

    @Test
    void findActiveStudentInTenant_requiresOrganizationRoleActiveStatusAndNotDeleted() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        User student = account(studentId, tenantId, Role.STUDENT);
        when(userRepository.findActiveByUserAndOrganization(studentId, tenantId))
                .thenReturn(Optional.of(student));

        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).contains(student);

        student.suspend();
        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).isEmpty();

        student.reactivate();
        student.setRoles(new HashSet<>(Set.of(Role.PROCTOR)));
        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).isEmpty();
    }

    @Test
    void findActiveStudentInTenant_unknownAccount_isEmpty() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(userRepository.findActiveByUserAndOrganization(studentId, tenantId)).thenReturn(Optional.empty());

        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).isEmpty();
    }

    @Test
    void isMemberOf_trueOnlyForAnAccountOfTheOrganization() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(userRepository.findActiveByUserAndOrganization(userId, tenantId))
                .thenReturn(Optional.of(account(userId, tenantId, Role.STUDENT)));
        when(userRepository.findActiveByUserAndOrganization(strangerId, tenantId)).thenReturn(Optional.empty());

        assertThat(service.isMemberOf(userId, tenantId)).isTrue();
        assertThat(service.isMemberOf(strangerId, tenantId)).isFalse();
    }

    @Test
    void requireActiveStudentsInTenant_failsClosedForMixedEligibility() {
        UUID tenantId = UUID.randomUUID();
        UUID activeStudentId = UUID.randomUUID();
        UUID suspendedStudentId = UUID.randomUUID();
        User active = account(activeStudentId, tenantId, Role.STUDENT);
        User suspended = account(suspendedStudentId, tenantId, Role.STUDENT);
        suspended.suspend();
        when(userRepository.findActiveByUsersAndOrganization(List.of(activeStudentId, suspendedStudentId), tenantId))
                .thenReturn(List.of(active, suspended));

        assertThatThrownBy(() -> service.requireActiveStudentsInTenant(
                List.of(activeStudentId, suspendedStudentId), tenantId))
                .isInstanceOf(IdentityService.IdentityNotEligibleException.class);
    }

    @Test
    void lockActiveStudentsInTenant_sortsIdsAndLocksOnlyEligibleStudents() {
        UUID tenantId = UUID.randomUUID();
        UUID firstStudentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondStudentId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(userRepository.findWithLockByUsersAndOrganization(List.of(firstStudentId, secondStudentId), tenantId))
                .thenReturn(List.of(account(secondStudentId, tenantId, Role.STUDENT),
                        account(firstStudentId, tenantId, Role.STUDENT)));

        service.lockActiveStudentsInTenant(List.of(secondStudentId, firstStudentId, firstStudentId), tenantId);

        verify(userRepository).findWithLockByUsersAndOrganization(
                List.of(firstStudentId, secondStudentId), tenantId);
    }

    @Test
    void lockActiveStudentsInTenant_failsClosedWhenLockedRowsAreNotEligible() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        User suspendedStudent = account(studentId, tenantId, Role.STUDENT);
        suspendedStudent.suspend();
        when(userRepository.findWithLockByUsersAndOrganization(List.of(studentId), tenantId))
                .thenReturn(List.of(suspendedStudent));

        assertThatThrownBy(() -> service.lockActiveStudentsInTenant(List.of(studentId), tenantId))
                .isInstanceOf(IdentityService.IdentityNotEligibleException.class);
    }

    @Test
    void lockActiveStudentsInTenant_studentOfAnotherOrganizationIsNotLocked_failsClosed() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        // the organization-scoped lock query finds nothing for an account of another organization
        when(userRepository.findWithLockByUsersAndOrganization(List.of(studentId), tenantId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.lockActiveStudentsInTenant(List.of(studentId), tenantId))
                .isInstanceOf(IdentityService.IdentityNotEligibleException.class);
    }

    @Test
    void countStudents_delegatesToRoleAwareRepositoryQuery() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.countByOrganizationIdAndRole(tenantId, Role.STUDENT)).thenReturn(487L);

        assertThat(service.countStudents(tenantId)).isEqualTo(487L);

        verify(userRepository).countByOrganizationIdAndRole(tenantId, Role.STUDENT);
    }

    @Test
    void findFullNames_mapsTenantAccountsByPublicId() {
        UUID tenantId = UUID.randomUUID();
        User ann = account(UUID.randomUUID(), tenantId, Role.STUDENT);
        ann.setFullName("Ann Nguyen");
        UUID annId = ann.getPublicId();
        when(userRepository.findActiveByUsersAndOrganization(List.of(annId), tenantId)).thenReturn(List.of(ann));

        assertThat(service.findFullNames(tenantId, List.of(annId)))
                .containsExactly(java.util.Map.entry(annId, "Ann Nguyen"));
    }

    @Test
    void findFullNames_withoutIds_doesNotQuery() {
        assertThat(service.findFullNames(UUID.randomUUID(), List.of())).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void lockActiveRoleMember_nullTenant_locksThePlatformAccountByPublicId() {
        UUID publicId = UUID.randomUUID();
        when(userRepository.findWithLockByPublicId(publicId))
                .thenReturn(Optional.of(account(publicId, null, Role.PLATFORM_ADMIN)));

        assertThat(service.lockActiveRoleMember(publicId, null, Role.PLATFORM_ADMIN)).isTrue();
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never())
                .findWithLockByUserAndOrganization(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lockActiveRoleMember_withTenant_usesTheOrganizationScopedLock() {
        UUID tenantId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(userRepository.findWithLockByUserAndOrganization(publicId, tenantId))
                .thenReturn(Optional.of(account(publicId, tenantId, Role.PROCTOR)));

        assertThat(service.lockActiveRoleMember(publicId, tenantId, Role.PROCTOR)).isTrue();
        assertThat(service.lockActiveRoleMember(publicId, tenantId, Role.EXAMINER)).isFalse();
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never())
                .findWithLockByPublicId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lockActiveRoleMember_accountOfAnotherOrganization_isNotLocked() {
        UUID tenantId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(userRepository.findWithLockByUserAndOrganization(publicId, tenantId)).thenReturn(Optional.empty());

        assertThat(service.lockActiveRoleMember(publicId, tenantId, Role.PROCTOR)).isFalse();
    }

    @Test
    void createHostAdmin_createsANewAccountWithAGeneratedUsernameAndPassword() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.existsByUsername(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HostAdminCreated created = service.createHostAdmin(tenantId, "acme", "contact@acme.example");

        assertThat(created.user().getUsername()).isEqualTo("acme.contact");
        assertThat(created.user().getEmail()).isEqualTo("contact@acme.example");
        assertThat(created.generatedPassword()).isNotBlank();
        assertThat(passwordEncoder.matches(created.generatedPassword(), created.user().getCredentialHash())).isTrue();
        assertThat(created.user().getOrganizationId()).isEqualTo(tenantId);
        assertThat(created.user().getRoles()).containsExactly(Role.HOST_ADMIN);
        verify(userRepository, org.mockito.Mockito.never()).findByUsernameAndDeletedFalse(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void createHostAdmin_emailUsedByAnotherOrganization_stillCreatesItsOwnAccount() {
        when(userRepository.existsByUsername(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        HostAdminCreated first = service.createHostAdmin(tenantA, "acme", "same@mail.example");
        HostAdminCreated second = service.createHostAdmin(tenantB, "beta", "same@mail.example");

        assertThat(first.user()).isNotSameAs(second.user());
        assertThat(first.user().getUsername()).startsWith("acme.");
        assertThat(second.user().getUsername()).startsWith("beta.");
        assertThat(first.user().getOrganizationId()).isEqualTo(tenantA);
        assertThat(second.user().getOrganizationId()).isEqualTo(tenantB);
    }

    @Test
    void createHostAdmin_everyGeneratedUsernameIsTaken_failsInsteadOfReusingOne() {
        when(userRepository.existsByUsername(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.createHostAdmin(UUID.randomUUID(), "acme", "contact@acme.example"))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, org.mockito.Mockito.never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }
}
