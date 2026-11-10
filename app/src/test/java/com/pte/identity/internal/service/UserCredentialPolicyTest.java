package com.pte.identity.internal.service;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.auth.ChangePasswordRequest;
import com.pte.identity.internal.dto.request.auth.ResetPasswordRequest;
import com.pte.identity.internal.dto.response.GeneratedCredentialsResponse;
import com.pte.identity.internal.exception.StudentAttemptInProgressException;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.StartedAttemptLookup;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every way a credential can change follows one policy: a Student who is mid-exam is never touched, a credential set
 * by someone else must be changed again at the next sign-in, and every other device is signed out.
 */
@ExtendWith(MockitoExtension.class)
class UserCredentialPolicyTest {

    private static final String OLD_PASSWORD = "OldPassword123";

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
                refreshTokenService, startedAttemptLookup, java.time.Clock.systemUTC());
    }

    // ---- a Student who is mid-exam keeps the credential they are using ----

    @Test
    void generateStudentCredentials_whileStudentIsInExam_isRejectedAndChangesNothing() {
        Scenario scenario = hostManagesStudent(true);
        when(startedAttemptLookup.hasInProgressAttempt(scenario.userPublicId)).thenReturn(true);

        assertThatThrownBy(() -> userService.generateStudentCredentials(scenario.userPublicId, scenario.caller))
                .isInstanceOf(StudentAttemptInProgressException.class);

        assertNothingChanged(scenario);
    }

    @Test
    void sendGeneratedCredentials_whileStudentIsInExam_isRejectedAndChangesNothing() {
        Scenario scenario = hostManagesStudent(true);
        when(startedAttemptLookup.hasInProgressAttempt(scenario.userPublicId)).thenReturn(true);

        assertThatThrownBy(() -> userService.sendGeneratedCredentials(scenario.userPublicId, scenario.caller))
                .isInstanceOf(StudentAttemptInProgressException.class);

        assertNothingChanged(scenario);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resetPassword_whileStudentIsInExam_isRejectedAndChangesNothing() {
        Scenario scenario = hostManagesStudent(true);
        when(startedAttemptLookup.hasInProgressAttempt(scenario.userPublicId)).thenReturn(true);

        assertThatThrownBy(() -> userService.resetPassword(scenario.userPublicId,
                new ResetPasswordRequest("NewPassword456"), scenario.caller))
                .isInstanceOf(StudentAttemptInProgressException.class);

        assertNothingChanged(scenario);
    }

    @Test
    void changeOwnPassword_whileStudentIsInExam_isRejectedAndChangesNothing() {
        UUID userPublicId = UUID.randomUUID();
        User student = account(1L, userPublicId, OLD_PASSWORD);
        String hashBefore = student.getCredentialHash();
        lenient().when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(student));
        when(startedAttemptLookup.hasInProgressAttempt(userPublicId)).thenReturn(true);
        CurrentUser self = new CurrentUser(userPublicId, UUID.randomUUID(), List.of("STUDENT"));

        assertThatThrownBy(() -> userService.changeOwnPassword(
                new ChangePasswordRequest(OLD_PASSWORD, "NewPassword456", "raw-token"), self))
                .isInstanceOf(StudentAttemptInProgressException.class);

        assertThat(student.getCredentialHash()).isEqualTo(hashBefore);
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllOthers(any(), any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    // ---- a credential set by someone else is temporary and signs every device out ----

    @Test
    void resetPassword_forStudent_requiresChangeAtNextSignInAndRevokesEverySession() {
        Scenario scenario = hostManagesStudent(true);
        when(startedAttemptLookup.hasInProgressAttempt(scenario.userPublicId)).thenReturn(false);

        userService.resetPassword(scenario.userPublicId, new ResetPasswordRequest("NewPassword456"),
                scenario.caller);

        assertThat(passwordEncoder.matches("NewPassword456", scenario.user.getCredentialHash())).isTrue();
        assertThat(scenario.user.isMustChangePassword()).isTrue();
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void resetPassword_byPlatformAdmin_requiresChangeAndRevokesWithoutAskingAboutExams() {
        UUID userPublicId = UUID.randomUUID();
        User host = account(1L, userPublicId, OLD_PASSWORD);
        host.setRoles(Set.of(Role.HOST_ADMIN));
        when(userRepository.findByPublicIdAndDeletedFalse(userPublicId)).thenReturn(Optional.of(host));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of());
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        when(provisioningHelper.canManageTarget(admin, host.getRoles())).thenReturn(true);

        userService.resetPassword(userPublicId, new ResetPasswordRequest("NewPassword456"), admin);

        assertThat(host.isMustChangePassword()).isTrue();
        verify(refreshTokenService).revokeAllForUser(1L);
        verify(startedAttemptLookup, never()).hasInProgressAttempt(any());
    }

    @Test
    void generateStudentCredentials_revokesEverySession() {
        Scenario scenario = hostManagesStudent(false);
        when(startedAttemptLookup.hasInProgressAttempt(scenario.userPublicId)).thenReturn(false);

        userService.generateStudentCredentials(scenario.userPublicId, scenario.caller);

        assertThat(scenario.user.isMustChangePassword()).isTrue();
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    // ---- the Host can e-mail a Student their temporary password ----

    @Test
    void sendGeneratedCredentials_forStudentWithEmail_mailsTemporaryPasswordAndAudits() {
        Scenario scenario = hostManagesStudent(true);
        when(startedAttemptLookup.hasInProgressAttempt(scenario.userPublicId)).thenReturn(false);

        GeneratedCredentialsResponse response =
                userService.sendGeneratedCredentials(scenario.userPublicId, scenario.caller);

        ArgumentCaptor<UserCredentialsEmailRequestedEvent> event =
                ArgumentCaptor.forClass(UserCredentialsEmailRequestedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().email()).isEqualTo("student@tenant.example");
        assertThat(event.getValue().temporaryPassword()).isEqualTo(response.temporaryPassword());
        assertThat(response.emailQueued()).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, scenario.user.getCredentialHash())).isFalse();
        assertThat(passwordEncoder.matches(response.temporaryPassword(), scenario.user.getCredentialHash()))
                .isTrue();
        assertThat(scenario.user.isMustChangePassword()).isTrue();
        verify(refreshTokenService).revokeAllForUser(1L);
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq(scenario.caller), eq(IdentityConstants.USER_AGGREGATE),
                eq(scenario.userPublicId.toString()), eq(IdentityConstants.USER_CREDENTIALS_EMAILED),
                summary.capture());
        assertThat(summary.getValue()).doesNotContain(response.temporaryPassword());
    }

    @Test
    void sendGeneratedCredentials_forStaff_doesNotAskAboutExamsButStillRevokesSessions() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User proctor = account(1L, userPublicId, OLD_PASSWORD);
        proctor.setEmail("proctor@tenant.example");
        OrganizationMember member = memberOf(proctor, tenantId, Role.PROCTOR);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(member);
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(host, member.getRoles())).thenReturn(true);
        when(memberDirectory.isExclusivelyOwned(member)).thenReturn(true);

        userService.sendGeneratedCredentials(userPublicId, host);

        verify(startedAttemptLookup, never()).hasInProgressAttempt(any());
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    // ---- changing your own password signs the other devices out, keeping this one ----

    @Test
    void changeOwnPassword_withRefreshToken_keepsThatSessionAndRevokesTheOthers() {
        UUID userPublicId = UUID.randomUUID();
        User staff = account(1L, userPublicId, OLD_PASSWORD);
        staff.setMustChangePassword(true);
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(staff));
        CurrentUser self = new CurrentUser(userPublicId, UUID.randomUUID(), List.of("HOST_ADMIN"));

        userService.changeOwnPassword(new ChangePasswordRequest(OLD_PASSWORD, "NewPassword456", "raw-token"), self);

        assertThat(passwordEncoder.matches("NewPassword456", staff.getCredentialHash())).isTrue();
        assertThat(staff.isMustChangePassword()).isFalse();
        verify(refreshTokenService).revokeAllOthers(1L, "raw-token");
        verify(refreshTokenService, never()).revokeAllForUser(any());
        verify(auditLogService).record(eq(self), eq(IdentityConstants.USER_AGGREGATE),
                eq(userPublicId.toString()), eq(IdentityConstants.USER_PASSWORD_CHANGED_SELF), any());
    }

    @Test
    void changeOwnPassword_withoutRefreshToken_revokesNothingSoOlderClientsKeepWorking() {
        UUID userPublicId = UUID.randomUUID();
        User staff = account(1L, userPublicId, OLD_PASSWORD);
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(staff));
        CurrentUser self = new CurrentUser(userPublicId, UUID.randomUUID(), List.of("HOST_ADMIN"));

        userService.changeOwnPassword(new ChangePasswordRequest(OLD_PASSWORD, "NewPassword456"), self);

        assertThat(passwordEncoder.matches("NewPassword456", staff.getCredentialHash())).isTrue();
        verify(refreshTokenService, never()).revokeAllOthers(any(), any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    // ---- helpers ----

    private record Scenario(UUID userPublicId, User user, CurrentUser caller, String hashBefore) {
    }

    /** A Host (same organization, sole owner of the account) managing a Student, optionally with an e-mail. */
    private Scenario hostManagesStudent(boolean withEmail) {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = account(1L, userPublicId, OLD_PASSWORD);
        user.setUsername("tenant.student");
        user.setEmail(withEmail ? "student@tenant.example" : null);
        OrganizationMember member = memberOf(user, tenantId, Role.STUDENT);
        when(memberDirectory.requireMember(tenantId, userPublicId)).thenReturn(member);
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(host, member.getRoles())).thenReturn(true);
        when(memberDirectory.isExclusivelyOwned(member)).thenReturn(true);
        return new Scenario(userPublicId, user, host, user.getCredentialHash());
    }

    private void assertNothingChanged(Scenario scenario) {
        assertThat(scenario.user.getCredentialHash()).isEqualTo(scenario.hashBefore);
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
        verify(refreshTokenService, never()).revokeAllOthers(any(), any());
    }

    private User account(Long id, UUID publicId, String password) {
        User user = new User();
        user.setId(id);
        user.setPublicId(publicId);
        user.setUsername("user@tenant.example");
        user.setEmail("user@tenant.example");
        user.setFullName("Test User");
        user.setStatus(UserStatus.ACTIVE);
        user.setCredentialHash(passwordEncoder.encode(password));
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
}
