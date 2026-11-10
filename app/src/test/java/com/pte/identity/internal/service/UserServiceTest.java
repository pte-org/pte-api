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
import com.pte.identity.domain.OrganizationMember;
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
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
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
import static org.mockito.Mockito.when;

/**
 * An account is global and carries the credential; roles and suspension of a tenant user live on its
 * {@link OrganizationMember}. Tenant callers reach only members of their own organization.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

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
        userService = new UserService(userRepository, memberRepository, memberDirectory, passwordEncoder,
                provisioningHelper, bulkCreateWriter, tenancyService, eventPublisher, auditLogService,
                refreshTokenService, startedAttemptLookup,
                Clock.fixed(Instant.parse("2026-11-09T03:00:00Z"), ZoneOffset.UTC));
    }

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

    private OrganizationMember memberOf(User user, UUID organizationId, Role role) {
        OrganizationMember member = new OrganizationMember();
        member.setUser(user);
        member.setOrganizationId(organizationId);
        member.setStatus(UserStatus.ACTIVE);
        member.setRoles(new HashSet<>(Set.of(role)));
        return member;
    }

    @Test
    void me_returnsOrganizationType_fromTenancyModule() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = account(1L, userPublicId, null);
        OrganizationMember member = memberOf(user, tenantId, Role.HOST_ADMIN);
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));
        when(memberDirectory.find(tenantId, userPublicId)).thenReturn(Optional.of(member));
        when(tenancyService.findOrganizationType(tenantId)).thenReturn(Optional.of("SCHOOL"));

        UserResponse response = userService.me(new CurrentUser(userPublicId, tenantId, List.of("HOST_ADMIN")));

        assertThat(response.organizationType()).isEqualTo("SCHOOL");
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.roles()).containsExactly("HOST_ADMIN");
    }

    @Test
    void suspendLocksTheMembershipBeforeChangingExaminerEligibility() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OrganizationMember examiner = memberOf(account(1L, userPublicId, null), tenantId, Role.EXAMINER);
        when(memberDirectory.requireMemberForUpdate(tenantId, userPublicId)).thenReturn(examiner);

        UserResponse response = userService.suspend(userPublicId,
                new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN")));

        assertThat(response.status()).isEqualTo("SUSPENDED");
        assertThat(examiner.isSuspended()).isTrue();
        verify(memberDirectory).requireMemberForUpdate(tenantId, userPublicId);
        verify(refreshTokenService).revokeForMembership(1L, tenantId);
    }

    @Test
    void reactivateAlsoLocksTheMembershipBeforeChangingExaminerEligibility() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OrganizationMember examiner = memberOf(account(1L, userPublicId, null), tenantId, Role.EXAMINER);
        examiner.suspend();
        when(memberDirectory.requireMemberForUpdate(tenantId, userPublicId)).thenReturn(examiner);

        UserResponse response = userService.reactivate(userPublicId,
                new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN")));

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(memberDirectory).requireMemberForUpdate(tenantId, userPublicId);
    }

    @Test
    void suspendByHost_leavesTheAccountItselfActiveForItsOtherOrganizations() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = account(1L, userPublicId, null);
        OrganizationMember member = memberOf(user, tenantId, Role.STUDENT);
        when(memberDirectory.requireMemberForUpdate(tenantId, userPublicId)).thenReturn(member);

        userService.suspend(userPublicId, new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN")));

        assertThat(member.isSuspended()).isTrue();
        assertThat(user.isSuspended()).isFalse();
    }

    @Test
    void resetPassword_overwritesHash_soOldPasswordNoLongerMatchesAndNewOneDoes() {
        UUID userPublicId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        User user = account(1L, userPublicId, "OldPassword123");
        user.setRoles(Set.of(Role.PLATFORM_AUTHOR));
        when(userRepository.findByPublicIdAndDeletedFalse(userPublicId)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of());

        CurrentUser caller = new CurrentUser(callerId, null, List.of("PLATFORM_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, user.getRoles())).thenReturn(true);
        UserResponse response = userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(passwordEncoder.matches("OldPassword123", user.getCredentialHash())).isFalse();
        assertThat(passwordEncoder.matches("NewPassword456", user.getCredentialHash())).isTrue();
        verify(userRepository).save(user);
        verify(auditLogService).record(eq(caller), eq(IdentityConstants.USER_AGGREGATE),
                eq(userPublicId.toString()), eq(IdentityConstants.USER_PASSWORD_RESET),
                eq(IdentityConstants.USER_PASSWORD_RESET_SUMMARY));
    }

    @Test
    void resetPassword_userNotFound_throwsAndNeverSavesACredential() {
        UUID userPublicId = UUID.randomUUID();
        when(userRepository.findByPublicIdAndDeletedFalse(userPublicId)).thenReturn(Optional.empty());

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatThrownBy(() ->
                userService.resetPassword(userPublicId, new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void listForTenant_returnsOnlyThatOrganizationsMembers() {
        UUID tenantId = UUID.randomUUID();
        OrganizationMember member = memberOf(account(1L, UUID.randomUUID(), null), tenantId, Role.HOST_ADMIN);
        when(memberRepository.findActiveByOrganization(tenantId)).thenReturn(List.of(member));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, member.getRoles())).thenReturn(true);

        List<UserResponse> result = userService.listForTenant(tenantId, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tenantId()).isEqualTo(tenantId);
    }

    @Test
    void listForTenant_noMembers_returnsEmptyList() {
        UUID tenantId = UUID.randomUUID();
        when(memberRepository.findActiveByOrganization(tenantId)).thenReturn(List.of());

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        assertThat(userService.listForTenant(tenantId, caller)).isEmpty();
    }

    @Test
    void create_newEmail_createsAccountAndMembership_andRoundTripsProfileFields() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        CreateUserRequest request = new CreateUserRequest(
                "student@tenant.example", "Student One", "Password123",
                List.of("STUDENT"), null,
                "SC-001", "12A1", "0900000000", LocalDate.of(2008, 5, 1));

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.STUDENT));
        when(memberRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            user.setPublicId(UUID.randomUUID());
            return user;
        });
        when(memberRepository.saveAndFlush(any(OrganizationMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.create(request, caller);

        verify(tenancyService).assertCanAddStudents(tenantId, 1L);
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.roles()).containsExactly("STUDENT");
        assertThat(response.studentCode()).isEqualTo("SC-001");
        assertThat(response.className()).isEqualTo("12A1");
        assertThat(response.phone()).isEqualTo("0900000000");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(2008, 5, 1));
    }

    @Test
    void create_emailOfExistingAccount_joinsThatAccountToTheOrganizationWithoutANewAccount() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        CreateUserRequest request = new CreateUserRequest(
                "host@tenant.example", "Host Admin", "Password123",
                List.of("HOST_ADMIN"), null, null, null, null, null);
        User existing = account(7L, UUID.randomUUID(), "KeepThisPassword1");
        String existingHash = existing.getCredentialHash();

        when(userRepository.findByUsername(request.email())).thenReturn(Optional.of(existing));
        when(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(7L, tenantId))
                .thenReturn(Optional.empty());
        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.HOST_ADMIN));
        when(memberRepository.saveAndFlush(any(OrganizationMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.create(request, caller);

        assertThat(response.publicId()).isEqualTo(existing.getPublicId());
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(existing.getCredentialHash()).isEqualTo(existingHash);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void create_emailAlreadyMemberOfTheOrganization_isRejected() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        CreateUserRequest request = new CreateUserRequest(
                "host@tenant.example", "Host Admin", "Password123",
                List.of("HOST_ADMIN"), null, null, null, null, null);
        User existing = account(7L, UUID.randomUUID(), null);

        when(userRepository.findByUsername(request.email())).thenReturn(Optional.of(existing));
        when(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(7L, tenantId))
                .thenReturn(Optional.of(memberOf(existing, tenantId, Role.HOST_ADMIN)));
        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.HOST_ADMIN));

        assertThatThrownBy(() -> userService.create(request, caller)).isInstanceOf(EmailAlreadyUsedException.class);

        verify(memberRepository, never()).saveAndFlush(any(OrganizationMember.class));
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
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        BulkCreateUserRow rowA = row("a@tenant.example");
        BulkCreateUserRow rowB = row("b@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(rowA, rowB), null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(memberRepository.findActiveByOrganizationAndEmails(tenantId, List.of(rowA.email(), rowB.email())))
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
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
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
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
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
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        BulkCreateUserRow existing = row("existing@tenant.example");
        BulkCreateUserRow fresh = row("fresh@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(existing, fresh), null);

        User existingUser = new User();
        existingUser.setEmail(existing.email());

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(memberRepository.findActiveByOrganizationAndEmails(tenantId, List.of(existing.email(), fresh.email())))
                .thenReturn(List.of(memberOf(existingUser, tenantId, Role.STUDENT)));
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
    void createBulk_writerLosesConcurrentRaceEveryRetry_skipsThatRowOnly_stillCreatesOthers() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        BulkCreateUserRow raced = row("raced@tenant.example");
        BulkCreateUserRow fine = row("fine@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(raced, fine), null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(memberRepository.findActiveByOrganizationAndEmails(tenantId, List.of(raced.email(), fine.email())))
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
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        BulkCreateUserRow rowA = row("a@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(rowA), null, true);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(memberRepository.findActiveByOrganizationAndEmails(tenantId, List.of(rowA.email())))
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
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        CreateUserRequest request = new CreateUserRequest(
                "student@tenant.example", "Student One", "Password123",
                List.of("STUDENT"), null, null, null, null, null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.STUDENT));
        when(memberRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.saveAndFlush(any(OrganizationMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        CreateUserRequest request = new CreateUserRequest(
                "student@tenant.example", "Student One", "Password123",
                List.of("STUDENT"), null, null, null, null, null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.STUDENT));
        when(memberRepository.findActiveByOrganizationAndEmails(tenantId, List.of(request.email())))
                .thenReturn(List.of());
        doThrow(new StudentLimitExceededException(100L, 100L, 1L))
                .when(tenancyService).assertCanAddStudents(tenantId, 1L);

        assertThatThrownBy(() -> userService.create(request, caller))
                .isInstanceOf(StudentLimitExceededException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(memberRepository, never()).saveAndFlush(any(OrganizationMember.class));
    }

    @Test
    void changeOwnPassword_updatesHashAndClearsFirstLoginFlag() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User student = account(1L, userPublicId, "Temporary123");
        student.setMustChangePassword(true);
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(student));

        userService.changeOwnPassword(new ChangePasswordRequest("Temporary123", "NewPassword456"),
                new CurrentUser(userPublicId, tenantId, List.of("STUDENT")));

        assertThat(passwordEncoder.matches("NewPassword456", student.getCredentialHash())).isTrue();
        assertThat(student.isMustChangePassword()).isFalse();
        verify(userRepository).save(student);
    }

    @Test
    void resetPassword_hostAdmin_succeedsAgainstSameTenantStudent() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = account(1L, userPublicId, "OldPassword123");
        OrganizationMember student = memberOf(user, tenantId, Role.STUDENT);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(student);

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, student.getRoles())).thenReturn(true);
        when(memberDirectory.isExclusivelyOwned(student)).thenReturn(true);
        UserResponse response = userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(passwordEncoder.matches("NewPassword456", user.getCredentialHash())).isTrue();
    }

    @Test
    void sendGeneratedCredentials_rotatesHash_marksFirstLogin_andPublishesEmailEvent() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = account(1L, userPublicId, "OldPassword123");
        user.setUsername("tenant.proctor");
        user.setEmail("proctor@tenant.example");
        OrganizationMember examStaff = memberOf(user, tenantId, Role.PROCTOR);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(examStaff);

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, examStaff.getRoles())).thenReturn(true);
        when(memberDirectory.isExclusivelyOwned(examStaff)).thenReturn(true);

        GeneratedCredentialsResponse response = userService.sendGeneratedCredentials(userPublicId, caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(response.username()).isEqualTo("tenant.proctor");
        assertThat(response.email()).isEqualTo("proctor@tenant.example");
        assertThat(response.temporaryPassword()).matches("^[2-9A-HJ-NP-Za-hj-np-z]{4}-[2-9A-HJ-NP-Za-hj-np-z]{4}$");
        assertThat(response.emailQueued()).isTrue();
        assertThat(passwordEncoder.matches("OldPassword123", user.getCredentialHash())).isFalse();
        assertThat(passwordEncoder.matches(response.temporaryPassword(), user.getCredentialHash())).isTrue();
        assertThat(user.isMustChangePassword()).isTrue();
        verify(eventPublisher).publishEvent(any(UserCredentialsEmailRequestedEvent.class));
        verify(userRepository).save(user);
    }

    @Test
    void sendGeneratedCredentials_withoutEmail_rejectsBeforeHashChange() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = account(1L, userPublicId, "OldPassword123");
        String hashBefore = user.getCredentialHash();
        user.setUsername("tenant.proctor");
        user.setEmail(null);
        OrganizationMember examStaff = memberOf(user, tenantId, Role.PROCTOR);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(examStaff);

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, examStaff.getRoles())).thenReturn(true);
        when(memberDirectory.isExclusivelyOwned(examStaff)).thenReturn(true);

        assertThatThrownBy(() -> userService.sendGeneratedCredentials(userPublicId, caller))
                .isInstanceOf(com.pte.identity.internal.exception.UserEmailRequiredException.class);

        assertThat(user.getCredentialHash()).isEqualTo(hashBefore);
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void generateStudentCredentials_rotatesHash_withoutPublishingEmailEvent() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = account(1L, userPublicId, "OldPassword123");
        user.setUsername("tenant.student");
        user.setEmail(null);
        OrganizationMember student = memberOf(user, tenantId, Role.STUDENT);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(student);

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, student.getRoles())).thenReturn(true);
        when(memberDirectory.isExclusivelyOwned(student)).thenReturn(true);

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
    void resetPassword_hostAdmin_againstSameTenantHostAdmin_throwsForbidden() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OrganizationMember fellowHostAdmin = memberOf(account(1L, userPublicId, "OldPassword123"), tenantId,
                Role.HOST_ADMIN);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(fellowHostAdmin);

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
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

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_platformAdmin_stillWorksAgainstHostAdminAnyTenant() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = account(1L, userPublicId, "OldPassword123");
        OrganizationMember hostAdmin = memberOf(user, tenantId, Role.HOST_ADMIN);
        when(userRepository.findByPublicIdAndDeletedFalse(userPublicId)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of(hostAdmin));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
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
        User user = account(1L, userPublicId, "OldPassword123");
        OrganizationMember student = memberOf(user, tenantId, Role.STUDENT);
        when(userRepository.findByPublicIdAndDeletedFalse(userPublicId)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of(student));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, Set.of(Role.STUDENT))).thenReturn(false);

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(ForbiddenPasswordResetException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_hostAdmin_againstAccountSharedWithAnotherOrganization_throwsForbidden() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = account(1L, userPublicId, "OldPassword123");
        String hashBefore = user.getCredentialHash();
        OrganizationMember student = memberOf(user, tenantId, Role.STUDENT);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(student);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, student.getRoles())).thenReturn(true);
        when(memberDirectory.isExclusivelyOwned(student)).thenReturn(false);

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(ForbiddenPasswordResetException.class);

        assertThat(user.getCredentialHash()).isEqualTo(hashBefore);
        verify(userRepository, never()).save(any());
    }

    @Test
    void generateStudentCredentials_hostAdmin_againstSharedAccount_throwsForbidden() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = account(1L, userPublicId, "OldPassword123");
        OrganizationMember student = memberOf(user, tenantId, Role.STUDENT);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(student);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, student.getRoles())).thenReturn(true);

        assertThatThrownBy(() -> userService.generateStudentCredentials(userPublicId, caller))
                .isInstanceOf(ForbiddenPasswordResetException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_staffEmailOfPlatformAccount_isRejectedAndNeverJoinsTheOrganization() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        CreateUserRequest request = new CreateUserRequest(
                "admin@test.local", "Admin", "Password123",
                List.of("PROCTOR"), null, null, null, null, null);
        User platformAdmin = account(7L, UUID.randomUUID(), "KeepThisPassword1");
        platformAdmin.setRoles(Set.of(Role.PLATFORM_ADMIN));
        when(userRepository.findByUsername(request.email())).thenReturn(Optional.of(platformAdmin));
        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.PROCTOR));

        assertThatThrownBy(() -> userService.create(request, caller)).isInstanceOf(EmailAlreadyUsedException.class);

        verify(memberRepository, never()).saveAndFlush(any(OrganizationMember.class));
    }

    @Test
    void create_staffEmailHeldByAnotherAccount_isRejected() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        CreateUserRequest request = new CreateUserRequest(
                "taken@tenant.example", "Student", "Password123",
                List.of("PROCTOR"), null, null, null, null, null);
        when(userRepository.findByUsername(request.email())).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCaseAndDeletedFalse(request.email())).thenReturn(true);
        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.PROCTOR));

        assertThatThrownBy(() -> userService.create(request, caller)).isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }
}
