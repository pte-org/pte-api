package com.pte.identity.internal.service;

import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.assertj.core.api.Assertions.tuple;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.Clock;
import org.mockito.ArgumentCaptor;
import org.assertj.core.api.InstanceOfAssertFactories;
import com.pte.identity.internal.exception.RosterValidationException;
import com.pte.identity.internal.dto.response.RosterRowError;
import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.user.BulkCreateUserRow;
import com.pte.identity.internal.dto.request.user.BulkCreateUsersRequest;
import com.pte.identity.internal.dto.request.auth.ChangePasswordRequest;
import com.pte.identity.internal.dto.request.user.CreateUserRequest;
import com.pte.identity.internal.dto.request.auth.ResetPasswordRequest;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse;
import com.pte.identity.internal.dto.response.GeneratedCredentialsResponse;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import com.pte.identity.internal.exception.ForbiddenPasswordResetException;
import com.pte.identity.internal.exception.StudentAttemptInProgressException;
import com.pte.identity.internal.exception.UserEmailRequiredException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.StartedAttemptLookup;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.internal.exception.StudentLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * An account belongs to one organization (or none) and carries its own credential, roles and status. Tenant callers
 * reach only accounts of their own organization; an e-mail only has to be unique inside the organization.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationMemberDirectory memberDirectory;

    @Mock
    private UserProvisioningHelper provisioningHelper;

    @Mock
    private UserBulkCreateWriter bulkCreateWriter;

    @Mock
    private TenancyService tenancyService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private StartedAttemptLookup startedAttemptLookup;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, memberDirectory, passwordEncoder,
                provisioningHelper, bulkCreateWriter, tenancyService, eventPublisher, auditLogService,
                refreshTokenService, startedAttemptLookup,
                Clock.fixed(Instant.parse("2026-11-09T03:00:00Z"), ZoneOffset.UTC));
    }

    /** An account with no organization and no roles; the tests below place it into an organization. */
    private User account(Long id, UUID publicId, String oldPassword) {
        User user = new User();
        user.setId(id);
        user.setPublicId(publicId);
        user.setUsername("host@tenant.example");
        user.setEmail("host@tenant.example");
        user.setFullName("Host Admin");
        user.setStatus(UserStatus.ACTIVE);
        if (oldPassword != null) {
            user.setCredentialHash(passwordEncoder.encode(oldPassword));
        }
        return user;
    }

    private User accountOf(User user, UUID organizationId, Role role) {
        user.setOrganizationId(organizationId);
        user.setRoles(new HashSet<>(Set.of(role)));
        return user;
    }

    private User accountOf(UUID organizationId, Role role) {
        return accountOf(account(1L, UUID.randomUUID(), null), organizationId, role);
    }

    private CurrentUser host(UUID tenantId) {
        return new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    private CurrentUser platformAdmin() {
        return new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    }

    @Test
    void me_returnsOrganizationType_fromTenancyModule() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = accountOf(account(1L, userPublicId, null), tenantId, Role.HOST_ADMIN);
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));
        when(tenancyService.findOrganizationType(tenantId)).thenReturn(Optional.of("SCHOOL"));
        when(tenancyService.findOrganizationName(tenantId)).thenReturn(Optional.of("Trường Hoa Sen"));

        UserResponse response = userService.me(new CurrentUser(userPublicId, tenantId, List.of("HOST_ADMIN")));

        assertThat(response.organizationType()).isEqualTo("SCHOOL");
        assertThat(response.organizationName()).isEqualTo("Trường Hoa Sen");
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.roles()).containsExactly("HOST_ADMIN");
    }

    @Test
    void me_freePracticeStudentWithoutOrganization_hasNoTenantAndNeverAsksTenancy() {
        UUID userPublicId = UUID.randomUUID();
        User free = accountOf(account(1L, userPublicId, null), null, Role.STUDENT);
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(free));

        UserResponse response = userService.me(new CurrentUser(userPublicId, null, List.of("STUDENT")));

        assertThat(response.tenantId()).isNull();
        assertThat(response.organizationType()).isNull();
        assertThat(response.roles()).containsExactly("STUDENT");
        verifyNoInteractions(tenancyService);
    }

    @Test
    void suspend_locksTheAccountBeforeChangingItAndEndsItsSessions() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User examiner = accountOf(account(1L, userPublicId, null), tenantId, Role.EXAMINER);
        when(memberDirectory.requireMemberForUpdate(tenantId, userPublicId)).thenReturn(examiner);

        UserResponse response = userService.suspend(userPublicId, host(tenantId));

        assertThat(response.status()).isEqualTo("SUSPENDED");
        assertThat(examiner.isSuspended()).isTrue();
        verify(memberDirectory).requireMemberForUpdate(tenantId, userPublicId);
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void suspend_byPlatformAdmin_locksTheAccountByPublicId() {
        UUID userPublicId = UUID.randomUUID();
        User author = accountOf(account(1L, userPublicId, null), null, Role.PLATFORM_AUTHOR);
        when(userRepository.findWithLockByPublicId(userPublicId)).thenReturn(Optional.of(author));

        UserResponse response = userService.suspend(userPublicId, platformAdmin());

        assertThat(response.status()).isEqualTo("SUSPENDED");
        verify(refreshTokenService).revokeAllForUser(1L);
        verifyNoInteractions(memberDirectory);
    }

    @Test
    void suspend_accountOfAnotherOrganization_isNotFoundAndSessionsStayUntouched() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(memberDirectory.requireMemberForUpdate(tenantId, userPublicId)).thenThrow(new UserNotFoundException());

        assertThatThrownBy(() -> userService.suspend(userPublicId, host(tenantId)))
                .isInstanceOf(UserNotFoundException.class);

        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void reactivate_locksTheAccountAndMakesItActiveAgain() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User examiner = accountOf(account(1L, userPublicId, null), tenantId, Role.EXAMINER);
        examiner.suspend();
        when(memberDirectory.requireMemberForUpdate(tenantId, userPublicId)).thenReturn(examiner);

        UserResponse response = userService.reactivate(userPublicId, host(tenantId));

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(memberDirectory).requireMemberForUpdate(tenantId, userPublicId);
    }

    @Test
    void reactivate_isIdempotentForAnActiveAccount() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User active = accountOf(account(1L, userPublicId, null), tenantId, Role.STUDENT);
        when(memberDirectory.requireMemberForUpdate(tenantId, userPublicId)).thenReturn(active);

        UserResponse response = userService.reactivate(userPublicId, host(tenantId));

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void resetPassword_overwritesHash_soOldPasswordNoLongerMatchesAndNewOneDoes() {
        UUID userPublicId = UUID.randomUUID();
        User user = account(1L, userPublicId, "OldPassword123");
        user.setRoles(Set.of(Role.PLATFORM_AUTHOR));
        when(userRepository.findByPublicIdAndDeletedFalse(userPublicId)).thenReturn(Optional.of(user));

        CurrentUser caller = platformAdmin();
        when(provisioningHelper.canManageTarget(caller, user.getRoles())).thenReturn(true);
        UserResponse response = userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(passwordEncoder.matches("OldPassword123", user.getCredentialHash())).isFalse();
        assertThat(passwordEncoder.matches("NewPassword456", user.getCredentialHash())).isTrue();
        assertThat(user.isMustChangePassword()).isTrue();
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllForUser(1L);
        verify(auditLogService).record(eq(caller), eq(IdentityConstants.USER_AGGREGATE),
                eq(userPublicId.toString()), eq(IdentityConstants.USER_PASSWORD_RESET),
                eq(IdentityConstants.USER_PASSWORD_RESET_SUMMARY));
    }

    @Test
    void resetPassword_userNotFound_throwsAndNeverSavesACredential() {
        UUID userPublicId = UUID.randomUUID();
        when(userRepository.findByPublicIdAndDeletedFalse(userPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.resetPassword(userPublicId, new ResetPasswordRequest("NewPassword456"),
                        platformAdmin()))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_studentWhoIsMidExam_isRejectedAndKeepsTheirCredential() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User student = accountOf(account(1L, userPublicId, "OldPassword123"), tenantId, Role.STUDENT);
        String hashBefore = student.getCredentialHash();
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(student);
        CurrentUser caller = host(tenantId);
        when(provisioningHelper.canManageTarget(caller, student.getRoles())).thenReturn(true);
        when(startedAttemptLookup.hasInProgressAttempt(userPublicId)).thenReturn(true);

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(StudentAttemptInProgressException.class);

        assertThat(student.getCredentialHash()).isEqualTo(hashBefore);
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void listForTenant_returnsOnlyThatOrganizationsAccounts() {
        UUID tenantId = UUID.randomUUID();
        User member = accountOf(tenantId, Role.HOST_ADMIN);
        when(userRepository.findActiveByOrganization(tenantId)).thenReturn(List.of(member));

        CurrentUser caller = platformAdmin();
        when(provisioningHelper.canManageTarget(caller, member.getRoles())).thenReturn(true);

        List<UserResponse> result = userService.listForTenant(tenantId, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tenantId()).isEqualTo(tenantId);
    }

    @Test
    void listForTenant_dropsAccountsTheCallerMayNotManage() {
        UUID tenantId = UUID.randomUUID();
        User hostAdmin = accountOf(tenantId, Role.HOST_ADMIN);
        when(userRepository.findActiveByOrganization(tenantId)).thenReturn(List.of(hostAdmin));
        CurrentUser caller = host(tenantId);
        when(provisioningHelper.canManageTarget(caller, hostAdmin.getRoles())).thenReturn(false);

        assertThat(userService.listForTenant(tenantId, caller)).isEmpty();
    }

    @Test
    void listForTenant_noAccounts_returnsEmptyList() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.findActiveByOrganization(tenantId)).thenReturn(List.of());

        assertThat(userService.listForTenant(tenantId, platformAdmin())).isEmpty();
    }

    @Test
    void listByTenant_platformCallerWithoutOrganization_seesNothingAndNeverQueries() {
        assertThat(userService.listByTenant(platformAdmin())).isEmpty();
        assertThat(userService.listDirectory(platformAdmin())).isEmpty();

        verifyNoInteractions(userRepository);
    }

    @Test
    void create_newEmail_createsTheAccountInTheCallersOrganization_andRoundTripsProfileFields() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        CreateUserRequest request = new CreateUserRequest(
                "student@tenant.example", "Student One",
                List.of("STUDENT"), null,
                "SC-001", "12A1", "0900000000", LocalDate.of(2008, 5, 1));

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.STUDENT));
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            user.setPublicId(UUID.randomUUID());
            return user;
        });

        UserResponse response = userService.create(request, caller);

        verify(tenancyService).assertCanAddStudents(tenantId, 1L);
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.roles()).containsExactly("STUDENT");
        assertThat(response.studentCode()).isEqualTo("SC-001");
        assertThat(response.className()).isEqualTo("12A1");
        assertThat(response.phone()).isEqualTo("0900000000");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(2008, 5, 1));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(tenantId);
        assertThat(saved.getValue().getRoles()).containsExactly(Role.STUDENT);
    }

    @Test
    void create_staffWithSeveralRoles_keepsEveryRoleOnTheOneAccount() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        CreateUserRequest request = new CreateUserRequest(
                "both@tenant.example", "Both Roles",
                List.of("EXAMINER", "PROCTOR"), null, null, null, null, null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles()))
                .thenReturn(Set.of(Role.EXAMINER, Role.PROCTOR));
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request, caller);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(tenantId);
        assertThat(saved.getValue().getRoles()).containsExactlyInAnyOrder(Role.EXAMINER, Role.PROCTOR);
        verify(tenancyService, never()).assertCanAddStudents(any(), anyLong());
    }

    @Test
    void create_staff_getsAGeneratedUsernameInsteadOfTheEmail() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        CreateUserRequest request = new CreateUserRequest(
                "proctor@tenant.example", "Proctor One",
                List.of("PROCTOR"), null, null, null, null, null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.PROCTOR));
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request, caller);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUsername()).matches("school\\.[a-hj-km-np-z2-9]{8}");
        assertThat(saved.getValue().getEmail()).isEqualTo(request.email());
        verify(tenancyService, never()).assertCanAddStudents(any(), anyLong());
    }

    @Test
    void create_staff_emailsTheGeneratedUsernameAndATemporarySecretThatMustBeChanged() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        CreateUserRequest request = new CreateUserRequest(
                "proctor@tenant.example", "Proctor One",
                List.of("PROCTOR"), null, null, null, null, null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.PROCTOR));
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request, caller);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        ArgumentCaptor<UserCredentialsEmailRequestedEvent> event =
                ArgumentCaptor.forClass(UserCredentialsEmailRequestedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().username()).isEqualTo(saved.getValue().getUsername());
        assertThat(event.getValue().email()).isEqualTo("proctor@tenant.example");
        assertThat(event.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(event.getValue().temporaryPassword(),
                saved.getValue().getCredentialHash())).isTrue();
    }

    @Test
    void create_withoutEmail_isRejectedBeforeAnythingIsWritten() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        CreateUserRequest request = new CreateUserRequest(
                "  ", "Proctor One", List.of("PROCTOR"), null, null, null, null, null);
        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.PROCTOR));

        assertThatThrownBy(() -> userService.create(request, caller))
                .isInstanceOf(UserEmailRequiredException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void create_sameEmailInAnotherOrganization_isAllowed_butRejectedInTheSameOrganization() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        CurrentUser hostA = host(tenantA);
        CurrentUser hostB = host(tenantB);
        CreateUserRequest request = new CreateUserRequest(
                "shared@mail.example", "Shared Person",
                List.of("EXAMINER"), null, null, null, null, null);
        User existingInA = accountOf(tenantA, Role.EXAMINER);
        existingInA.setEmail("shared@mail.example");

        when(provisioningHelper.resolveTargetTenant(hostA, null)).thenReturn(tenantA);
        when(provisioningHelper.resolveTargetTenant(hostB, null)).thenReturn(tenantB);
        when(provisioningHelper.resolveAndAuthorizeRoles(any(CurrentUser.class), eq(request.roles())))
                .thenReturn(Set.of(Role.EXAMINER));
        // Each lookup is scoped to one organization, so the other organization's account is invisible to it.
        when(userRepository.findActiveByOrganizationAndEmails(tenantA, List.of(request.email())))
                .thenReturn(List.of(existingInA));
        when(userRepository.findActiveByOrganizationAndEmails(tenantB, List.of(request.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantB)).thenReturn("other");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> userService.create(request, hostA)).isInstanceOf(EmailAlreadyUsedException.class);
        UserResponse created = userService.create(request, hostB);

        assertThat(created.tenantId()).isEqualTo(tenantB);
        assertThat(created.email()).isEqualTo("shared@mail.example");
        verify(userRepository, times(1)).saveAndFlush(any(User.class));
        verify(userRepository, never()).existsByEmailIgnoreCaseAndDeletedFalse(anyString());
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void create_usernameAlreadyTaken_triesAnotherGeneratedOne() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        CreateUserRequest request = new CreateUserRequest(
                "proctor@tenant.example", "Proctor One",
                List.of("PROCTOR"), null, null, null, null, null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.PROCTOR));
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(userRepository.existsByUsername(anyString())).thenReturn(true, false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request, caller);

        verify(userRepository, times(2)).existsByUsername(anyString());
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void create_everyGeneratedUsernameIsTaken_failsWithoutSavingAnything() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        CreateUserRequest request = new CreateUserRequest(
                "proctor@tenant.example", "Proctor One",
                List.of("PROCTOR"), null, null, null, null, null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.PROCTOR));
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(userRepository.existsByUsername(anyString())).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request, caller))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void create_emailAlreadyUsedInTheOrganization_isRejected() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        CreateUserRequest request = new CreateUserRequest(
                "host@tenant.example", "Host Admin",
                List.of("HOST_ADMIN"), null, null, null, null, null);
        User existing = accountOf(account(7L, UUID.randomUUID(), null), tenantId, Role.HOST_ADMIN);

        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of(existing));
        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.HOST_ADMIN));

        assertThatThrownBy(() -> userService.create(request, caller)).isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    private BulkCreateUserRow row(String email) {
        return new BulkCreateUserRow(email, "Student " + email, null, null, null, null);
    }

    private UserBulkCreateWriter.Result writerResult(String email, String username, String password) {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName("Student " + email);
        return new UserBulkCreateWriter.Result(user, password);
    }

    @Test
    void createBulk_givesEveryRowAGeneratedUsername_evenWhenItHasAnEmail() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        BulkCreateUserRow rowA = row("a@tenant.example");
        BulkCreateUserRow rowB = row("b@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(rowA, rowB), null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(rowA.email(), rowB.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(bulkCreateWriter.createGeneratedStudent(anyString(), any(UserBulkCreateWriter.Row.class), eq(tenantId)))
                .thenReturn(Optional.of(writerResult(rowA.email(), "school.abcd2345", "Abcd-2345")))
                .thenReturn(Optional.of(writerResult(rowB.email(), "school.efgh6789", "Efgh-6789")));

        BulkCreateUsersResponse response = userService.createBulk(request, caller);

        assertThat(response.created()).extracting(BulkCreateUsersResponse.CreatedUser::username)
                .containsExactly("school.abcd2345", "school.efgh6789");
        assertThat(response.skipped()).isEmpty();
        ArgumentCaptor<String> usernames = ArgumentCaptor.forClass(String.class);
        verify(bulkCreateWriter, times(2)).createGeneratedStudent(usernames.capture(), any(), eq(tenantId));
        assertThat(usernames.getAllValues()).allMatch(username -> username.startsWith("school."));
        verify(tenancyService).assertCanAddStudents(tenantId, 2L);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createBulk_rowWithoutEmailOrFullName_rejectsWholeRosterWithEveryRowError() {
        CurrentUser caller = host(UUID.randomUUID());
        BulkCreateUserRow valid = row("ok@tenant.example");
        BulkCreateUserRow blank = new BulkCreateUserRow(null, " ", null, null, null, null);
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(valid, blank), null);
        when(provisioningHelper.resolveTargetTenant(any(), any())).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> userService.createBulk(request, caller))
                .isInstanceOfSatisfying(RosterValidationException.class, ex -> assertThat(ex.getData())
                        .asInstanceOf(InstanceOfAssertFactories.list(RosterRowError.class))
                        .containsExactly(
                                new RosterRowError(1, IdentityConstants.ROSTER_FIELD_EMAIL,
                                        IdentityConstants.ROSTER_EMAIL_REQUIRED),
                                new RosterRowError(1, IdentityConstants.ROSTER_FIELD_FULL_NAME,
                                        IdentityConstants.ROSTER_FULL_NAME_REQUIRED)));

        verify(bulkCreateWriter, never()).createGeneratedStudent(anyString(), any(), any());
        verify(tenancyService, never()).assertCanAddStudents(any(), anyLong());
    }

    @Test
    void createBulk_formatRules_reportInvalidEmailPhoneDateLengthAndDuplicate() {
        CurrentUser caller = host(UUID.randomUUID());
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(
                new BulkCreateUserRow("not-an-email", "A", null, null, "12ab", null),
                new BulkCreateUserRow("dup@tenant.example", "B", "x".repeat(51), null, "+84 901-234-567",
                        LocalDate.of(2008, 1, 15)),
                new BulkCreateUserRow("DUP@tenant.example", "C", null, null, null, LocalDate.of(2026, 11, 9))),
                null);
        when(provisioningHelper.resolveTargetTenant(any(), any())).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> userService.createBulk(request, caller))
                .isInstanceOfSatisfying(RosterValidationException.class, ex -> assertThat(ex.getData())
                        .asInstanceOf(InstanceOfAssertFactories.list(RosterRowError.class))
                        .extracting(RosterRowError::rowIndex, RosterRowError::code)
                        .containsExactly(
                                tuple(0, IdentityConstants.ROSTER_EMAIL_INVALID),
                                tuple(0, IdentityConstants.ROSTER_PHONE_INVALID),
                                tuple(1, IdentityConstants.ROSTER_STUDENT_CODE_TOO_LONG),
                                tuple(2, IdentityConstants.DUPLICATE_EMAIL_IN_BATCH),
                                tuple(2, IdentityConstants.ROSTER_DATE_OF_BIRTH_INVALID)));
    }

    @Test
    void createBulk_skipsEmailAlreadyInTheOrganization_butStillCreatesOthers() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        BulkCreateUserRow existing = row("existing@tenant.example");
        BulkCreateUserRow fresh = row("fresh@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(existing, fresh), null);

        User existingUser = accountOf(tenantId, Role.STUDENT);
        existingUser.setEmail(existing.email());

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(existing.email(), fresh.email())))
                .thenReturn(List.of(existingUser));
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(bulkCreateWriter.createGeneratedStudent(anyString(), any(UserBulkCreateWriter.Row.class), eq(tenantId)))
                .thenReturn(Optional.of(writerResult(fresh.email(), "school.ijkl2345", "Ijkl-2345")));

        BulkCreateUsersResponse response = userService.createBulk(request, caller);

        assertThat(response.created()).hasSize(1);
        assertThat(response.created().get(0).email()).isEqualTo(fresh.email());
        assertThat(response.skipped()).hasSize(1);
        assertThat(response.skipped().get(0).rowIndex()).isEqualTo(0);
        assertThat(response.skipped().get(0).reason()).isEqualTo(IdentityConstants.EMAIL_ALREADY_USED);
        verify(tenancyService).assertCanAddStudents(tenantId, 1L);
    }

    @Test
    void createBulk_emailOnlyKnownInAnotherOrganization_isStillCreatedHere() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        BulkCreateUserRow shared = row("shared@mail.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(shared), null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        // The lookup is scoped to this organization, so an account of another organization never shows up.
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(shared.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(bulkCreateWriter.createGeneratedStudent(anyString(), any(UserBulkCreateWriter.Row.class), eq(tenantId)))
                .thenReturn(Optional.of(writerResult(shared.email(), "school.qrst2345", "Qrst-2345")));

        BulkCreateUsersResponse response = userService.createBulk(request, caller);

        assertThat(response.created()).hasSize(1);
        assertThat(response.skipped()).isEmpty();
    }

    @Test
    void createBulk_writerLosesConcurrentRaceEveryRetry_skipsThatRowOnly_stillCreatesOthers() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        BulkCreateUserRow raced = row("raced@tenant.example");
        BulkCreateUserRow fine = row("fine@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(raced, fine), null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(raced.email(), fine.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(bulkCreateWriter.createGeneratedStudent(anyString(), any(UserBulkCreateWriter.Row.class), eq(tenantId)))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.empty())
                .thenReturn(Optional.of(writerResult(fine.email(), "school.mnop2345", "Mnop-2345")));

        BulkCreateUsersResponse response = userService.createBulk(request, caller);

        assertThat(response.created()).hasSize(1);
        assertThat(response.created().get(0).email()).isEqualTo(fine.email());
        assertThat(response.skipped()).singleElement()
                .satisfies(error -> {
                    assertThat(error.email()).isEqualTo(raced.email());
                    assertThat(error.reason()).isEqualTo(IdentityConstants.ROSTER_IMPORT_FAILED);
                });
        verify(tenancyService).assertCanAddStudents(tenantId, 2L);
    }

    @Test
    void createBulk_withSendCredentialsEmail_publishesOneEventPerCreatedStudent() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        BulkCreateUserRow rowA = row("a@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(rowA), null, true);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(rowA.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(bulkCreateWriter.createGeneratedStudent(anyString(), any(UserBulkCreateWriter.Row.class), eq(tenantId)))
                .thenReturn(Optional.of(writerResult(rowA.email(), "school.abcd2345", "Abcd-2345")));

        userService.createBulk(request, caller);

        ArgumentCaptor<UserCredentialsEmailRequestedEvent> event =
                ArgumentCaptor.forClass(UserCredentialsEmailRequestedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().username()).isEqualTo("school.abcd2345");
        assertThat(event.getValue().email()).isEqualTo(rowA.email());
        assertThat(event.getValue().temporaryPassword()).isEqualTo("Abcd-2345");
        assertThat(event.getValue().tenantId()).isEqualTo(tenantId);
    }

    @Test
    void create_student_getsGeneratedUsername_andEmailOnlyHasToBeUniqueInTheOrganization() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        CreateUserRequest request = new CreateUserRequest(
                "student@tenant.example", "Student One",
                List.of("STUDENT"), null, null, null, null, null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.STUDENT));
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request, caller);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUsername()).startsWith("school.");
        assertThat(saved.getValue().getEmail()).isEqualTo(request.email());
        verify(userRepository, never()).existsByEmailIgnoreCaseAndDeletedFalse(anyString());
    }

    @Test
    void create_studentLimitExceeded_rejectsBeforeInsert() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = host(tenantId);
        CreateUserRequest request = new CreateUserRequest(
                "student@tenant.example", "Student One",
                List.of("STUDENT"), null, null, null, null, null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.STUDENT));
        when(userRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of());
        doThrow(new StudentLimitExceededException(100L, 100L, 1L))
                .when(tenancyService).assertCanAddStudents(tenantId, 1L);

        assertThatThrownBy(() -> userService.create(request, caller))
                .isInstanceOf(StudentLimitExceededException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void changeOwnPassword_updatesHashAndClearsFirstLoginFlag() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User student = accountOf(account(1L, userPublicId, "Temporary123"), tenantId, Role.STUDENT);
        student.setMustChangePassword(true);
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(student));

        userService.changeOwnPassword(new ChangePasswordRequest("Temporary123", "NewPassword456"),
                new CurrentUser(userPublicId, tenantId, List.of("STUDENT")));

        assertThat(passwordEncoder.matches("NewPassword456", student.getCredentialHash())).isTrue();
        assertThat(student.isMustChangePassword()).isFalse();
        verify(userRepository).save(student);
    }

    @Test
    void changeOwnPassword_freePracticeStudentWithoutOrganization_canChangeTheirOwnPassword() {
        UUID userPublicId = UUID.randomUUID();
        User free = accountOf(account(1L, userPublicId, "Temporary123"), null, Role.STUDENT);
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(free));

        userService.changeOwnPassword(new ChangePasswordRequest("Temporary123", "NewPassword456"),
                new CurrentUser(userPublicId, null, List.of("STUDENT")));

        assertThat(passwordEncoder.matches("NewPassword456", free.getCredentialHash())).isTrue();
    }

    @Test
    void resetPassword_hostAdmin_succeedsAgainstSameTenantStudent_andEndsTheirSessions() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User student = accountOf(account(1L, userPublicId, "OldPassword123"), tenantId, Role.STUDENT);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(student);

        CurrentUser caller = host(tenantId);
        when(provisioningHelper.canManageTarget(caller, student.getRoles())).thenReturn(true);
        UserResponse response = userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(passwordEncoder.matches("NewPassword456", student.getCredentialHash())).isTrue();
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void sendGeneratedCredentials_rotatesHash_marksFirstLogin_andPublishesEmailEvent() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = accountOf(account(1L, userPublicId, "OldPassword123"), tenantId, Role.PROCTOR);
        user.setUsername("tenant.proctor");
        user.setEmail("proctor@tenant.example");
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(user);

        CurrentUser caller = host(tenantId);
        when(provisioningHelper.canManageTarget(caller, user.getRoles())).thenReturn(true);

        GeneratedCredentialsResponse response = userService.sendGeneratedCredentials(userPublicId, caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(response.username()).isEqualTo("tenant.proctor");
        assertThat(response.email()).isEqualTo("proctor@tenant.example");
        assertThat(response.temporaryPassword()).matches("^[2-9A-HJ-NP-Za-hj-np-z]{4}-[2-9A-HJ-NP-Za-hj-np-z]{4}$");
        assertThat(response.emailQueued()).isTrue();
        assertThat(passwordEncoder.matches("OldPassword123", user.getCredentialHash())).isFalse();
        assertThat(passwordEncoder.matches(response.temporaryPassword(), user.getCredentialHash())).isTrue();
        assertThat(user.isMustChangePassword()).isTrue();
        ArgumentCaptor<UserCredentialsEmailRequestedEvent> event =
                ArgumentCaptor.forClass(UserCredentialsEmailRequestedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().tenantId()).isEqualTo(tenantId);
        verify(userRepository).save(user);
    }

    @Test
    void sendGeneratedCredentials_withoutEmail_rejectsBeforeHashChange() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = accountOf(account(1L, userPublicId, "OldPassword123"), tenantId, Role.PROCTOR);
        String hashBefore = user.getCredentialHash();
        user.setUsername("tenant.proctor");
        user.setEmail(null);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(user);

        CurrentUser caller = host(tenantId);
        when(provisioningHelper.canManageTarget(caller, user.getRoles())).thenReturn(true);

        assertThatThrownBy(() -> userService.sendGeneratedCredentials(userPublicId, caller))
                .isInstanceOf(UserEmailRequiredException.class);

        assertThat(user.getCredentialHash()).isEqualTo(hashBefore);
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void generateStudentCredentials_rotatesHash_withoutPublishingEmailEvent() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = accountOf(account(1L, userPublicId, "OldPassword123"), tenantId, Role.STUDENT);
        user.setUsername("tenant.student");
        user.setEmail(null);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(user);

        CurrentUser caller = host(tenantId);
        when(provisioningHelper.canManageTarget(caller, user.getRoles())).thenReturn(true);

        GeneratedCredentialsResponse response = userService.generateStudentCredentials(userPublicId, caller);

        assertThat(response.username()).isEqualTo("tenant.student");
        assertThat(response.email()).isNull();
        assertThat(response.temporaryPassword()).matches("^[2-9A-HJ-NP-Za-hj-np-z]{4}-[2-9A-HJ-NP-Za-hj-np-z]{4}$");
        assertThat(response.emailQueued()).isFalse();
        assertThat(passwordEncoder.matches("OldPassword123", user.getCredentialHash())).isFalse();
        assertThat(passwordEncoder.matches(response.temporaryPassword(), user.getCredentialHash())).isTrue();
        assertThat(user.isMustChangePassword()).isTrue();
        verify(userRepository).save(user);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void generateStudentCredentials_forStaffTheCallerManages_rotatesHashWithoutEmail() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User proctor = accountOf(account(1L, userPublicId, "OldPassword123"), tenantId, Role.PROCTOR);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(proctor);
        CurrentUser caller = host(tenantId);
        when(provisioningHelper.canManageTarget(caller, proctor.getRoles())).thenReturn(true);

        GeneratedCredentialsResponse response = userService.generateStudentCredentials(userPublicId, caller);

        assertThat(response.emailQueued()).isFalse();
        assertThat(passwordEncoder.matches(response.temporaryPassword(), proctor.getCredentialHash())).isTrue();
        assertThat(proctor.isMustChangePassword()).isTrue();
        verify(userRepository).save(proctor);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void generateStudentCredentials_forAnAccountTheCallerCannotManage_throwsForbidden() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User fellowHostAdmin = accountOf(account(1L, userPublicId, "OldPassword123"), tenantId, Role.HOST_ADMIN);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(fellowHostAdmin);
        CurrentUser caller = host(tenantId);
        when(provisioningHelper.canManageTarget(caller, fellowHostAdmin.getRoles())).thenReturn(false);

        assertThatThrownBy(() -> userService.generateStudentCredentials(userPublicId, caller))
                .isInstanceOf(ForbiddenPasswordResetException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_hostAdmin_againstSameTenantHostAdmin_throwsForbidden() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User fellowHostAdmin = accountOf(account(1L, userPublicId, "OldPassword123"), tenantId, Role.HOST_ADMIN);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(fellowHostAdmin);

        CurrentUser caller = host(tenantId);
        when(provisioningHelper.canManageTarget(caller, fellowHostAdmin.getRoles())).thenReturn(false);

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(ForbiddenPasswordResetException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_hostAdmin_againstDifferentTenant_throwsUserNotFound() {
        UUID userPublicId = UUID.randomUUID();
        UUID callerTenantId = UUID.randomUUID();

        when(memberDirectory.requireMember(callerTenantId, userPublicId)).thenThrow(new UserNotFoundException());

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), host(callerTenantId)))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_platformAdmin_stillWorksAgainstHostAdminAnyTenant() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = accountOf(account(1L, userPublicId, "OldPassword123"), tenantId, Role.HOST_ADMIN);
        when(userRepository.findByPublicIdAndDeletedFalse(userPublicId)).thenReturn(Optional.of(user));

        CurrentUser caller = platformAdmin();
        when(provisioningHelper.canManageTarget(caller, Set.of(Role.HOST_ADMIN))).thenReturn(true);
        UserResponse response = userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(passwordEncoder.matches("NewPassword456", user.getCredentialHash())).isTrue();
    }

    @Test
    void resetPassword_platformAdmin_againstLowerTenantRole_throwsForbidden() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User student = accountOf(account(1L, userPublicId, "OldPassword123"), tenantId, Role.STUDENT);
        when(userRepository.findByPublicIdAndDeletedFalse(userPublicId)).thenReturn(Optional.of(student));

        CurrentUser caller = platformAdmin();
        when(provisioningHelper.canManageTarget(caller, Set.of(Role.STUDENT))).thenReturn(false);

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(ForbiddenPasswordResetException.class);

        verify(userRepository, never()).save(any());
    }
}
