package com.pte.identity;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
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

    @Mock
    private OrganizationMemberRepository memberRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private IdentityService service;

    @BeforeEach
    void setUp() {
        service = new IdentityService(userRepository, memberRepository, passwordEncoder);
    }

    private OrganizationMember member(UUID publicId, UUID tenantId, Role role) {
        User user = new User();
        user.setPublicId(publicId);
        user.setStatus(UserStatus.ACTIVE);
        OrganizationMember member = new OrganizationMember();
        member.setUser(user);
        member.setOrganizationId(tenantId);
        member.setStatus(UserStatus.ACTIVE);
        member.setRoles(new HashSet<>(Set.of(role)));
        return member;
    }

    @Test
    void findByTenantIdAndRole_returnsTheAccountsOfTheOrganizationsMembers() {
        UUID tenantId = UUID.randomUUID();
        OrganizationMember first = member(UUID.randomUUID(), tenantId, Role.HOST_ADMIN);
        OrganizationMember second = member(UUID.randomUUID(), tenantId, Role.HOST_ADMIN);
        when(memberRepository.findActiveByOrganizationAndRole(tenantId, Role.HOST_ADMIN))
                .thenReturn(List.of(first, second));

        List<User> result = service.findByTenantIdAndRole(tenantId, Role.HOST_ADMIN);

        assertThat(result).containsExactly(first.getUser(), second.getUser());
        verify(memberRepository).findActiveByOrganizationAndRole(tenantId, Role.HOST_ADMIN);
    }

    @Test
    void findByTenantIdAndRole_emptyResult() {
        UUID tenantId = UUID.randomUUID();
        when(memberRepository.findActiveByOrganizationAndRole(tenantId, Role.HOST_ADMIN)).thenReturn(List.of());

        assertThat(service.findByTenantIdAndRole(tenantId, Role.HOST_ADMIN)).isEmpty();
    }

    @Test
    void findActiveStudentInTenant_requiresMembershipRoleActiveStatusAndNotDeleted() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        OrganizationMember student = member(studentId, tenantId, Role.STUDENT);
        when(memberRepository.findActiveByUserAndOrganization(studentId, tenantId))
                .thenReturn(Optional.of(student));

        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).contains(student.getUser());

        student.suspend();
        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).isEmpty();

        student.reactivate();
        student.setRoles(new HashSet<>(Set.of(Role.PROCTOR)));
        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).isEmpty();
    }

    @Test
    void findActiveStudentInTenant_unknownMembership_isEmpty() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(memberRepository.findActiveByUserAndOrganization(studentId, tenantId)).thenReturn(Optional.empty());

        assertThat(service.findActiveStudentInTenant(studentId, tenantId)).isEmpty();
    }

    @Test
    void isMemberOf_trueOnlyForAnActiveMembership() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(memberRepository.findActiveByUserAndOrganization(userId, tenantId))
                .thenReturn(Optional.of(member(userId, tenantId, Role.STUDENT)));
        when(memberRepository.findActiveByUserAndOrganization(strangerId, tenantId)).thenReturn(Optional.empty());

        assertThat(service.isMemberOf(userId, tenantId)).isTrue();
        assertThat(service.isMemberOf(strangerId, tenantId)).isFalse();
    }

    @Test
    void requireActiveStudentsInTenant_failsClosedForMixedEligibility() {
        UUID tenantId = UUID.randomUUID();
        UUID activeStudentId = UUID.randomUUID();
        UUID suspendedStudentId = UUID.randomUUID();
        OrganizationMember active = member(activeStudentId, tenantId, Role.STUDENT);
        OrganizationMember suspended = member(suspendedStudentId, tenantId, Role.STUDENT);
        suspended.suspend();
        when(memberRepository.findActiveByUsersAndOrganization(List.of(activeStudentId, suspendedStudentId), tenantId))
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
        when(memberRepository.findWithLockByUsersAndOrganization(List.of(firstStudentId, secondStudentId), tenantId))
                .thenReturn(List.of(member(secondStudentId, tenantId, Role.STUDENT),
                        member(firstStudentId, tenantId, Role.STUDENT)));

        service.lockActiveStudentsInTenant(List.of(secondStudentId, firstStudentId, firstStudentId), tenantId);

        verify(memberRepository).findWithLockByUsersAndOrganization(
                List.of(firstStudentId, secondStudentId), tenantId);
    }

    @Test
    void lockActiveStudentsInTenant_failsClosedWhenLockedRowsAreNotEligible() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        OrganizationMember suspendedStudent = member(studentId, tenantId, Role.STUDENT);
        suspendedStudent.suspend();
        when(memberRepository.findWithLockByUsersAndOrganization(List.of(studentId), tenantId))
                .thenReturn(List.of(suspendedStudent));

        assertThatThrownBy(() -> service.lockActiveStudentsInTenant(List.of(studentId), tenantId))
                .isInstanceOf(IdentityService.IdentityNotEligibleException.class);
    }

    @Test
    void countStudents_delegatesToRoleAwareRepositoryQuery() {
        UUID tenantId = UUID.randomUUID();
        when(memberRepository.countByOrganizationIdAndRole(tenantId, Role.STUDENT)).thenReturn(487L);

        assertThat(service.countStudents(tenantId)).isEqualTo(487L);

        verify(memberRepository).countByOrganizationIdAndRole(tenantId, Role.STUDENT);
    }

    @Test
    void findFullNames_mapsTenantMembersByPublicId() {
        UUID tenantId = UUID.randomUUID();
        OrganizationMember ann = member(UUID.randomUUID(), tenantId, Role.STUDENT);
        ann.getUser().setFullName("Ann Nguyen");
        UUID annId = ann.getUser().getPublicId();
        when(memberRepository.findActiveByUsersAndOrganization(List.of(annId), tenantId)).thenReturn(List.of(ann));

        assertThat(service.findFullNames(tenantId, List.of(annId)))
                .containsExactly(java.util.Map.entry(annId, "Ann Nguyen"));
    }

    @Test
    void findFullNames_withoutIds_doesNotQuery() {
        assertThat(service.findFullNames(UUID.randomUUID(), List.of())).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(memberRepository, userRepository);
    }

    @Test
    void createHostAdmin_contactIsAPlatformAccount_isRejected() {
        UUID tenantId = UUID.randomUUID();
        User platformAdmin = new User();
        platformAdmin.setId(3L);
        platformAdmin.setRoles(Set.of(Role.PLATFORM_ADMIN));
        when(userRepository.findByUsernameAndDeletedFalse("admin@test.local")).thenReturn(Optional.of(platformAdmin));

        assertThatThrownBy(() -> service.createHostAdmin(tenantId, "admin@test.local"))
                .isInstanceOf(IdentityService.HostContactNotAllowedException.class);

        org.mockito.Mockito.verifyNoInteractions(memberRepository);
    }
}
