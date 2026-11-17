package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.config.AuthTokenProperties;
import com.pte.identity.internal.dto.request.auth.LoginRequest;
import com.pte.identity.internal.dto.request.auth.RefreshRequest;
import com.pte.identity.internal.dto.response.TokenResponse;
import com.pte.identity.internal.exception.InvalidLoginException;
import com.pte.identity.internal.exception.InvalidRefreshTokenException;
import com.pte.identity.internal.exception.TooManyLoginAttemptsException;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.AccessTokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login keys on the globally unique username. An account belongs to at most one organization and carries all its
 * roles: platform accounts and free practice students have no organization and sign in without one, every other
 * account lands in its own organization. One test per role (not a parameterized/looped test) so a single role
 * silently falling through is its own failure, not hidden inside a shared assertion.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "Password123!";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private LoginThrottle loginThrottle;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;
    private final AuthTokenProperties tokenProperties = new AuthTokenProperties();

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder,
                accessTokenIssuer, refreshTokenService, loginThrottle, tokenProperties);
    }

    private User activeUser(Long id, Set<Role> roles, String username) {
        User user = new User();
        user.setId(id);
        user.setPublicId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username);
        user.setFullName("Test User");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(roles);
        user.setCredentialHash(passwordEncoder.encode(PASSWORD));
        return user;
    }

    private User organizationAccount(Long id, UUID organizationId, Role role, String username) {
        User user = activeUser(id, Set.of(role), username);
        user.setOrganizationId(organizationId);
        return user;
    }

    private void assertPlatformLoginSucceeds(Role role) {
        String username = role.name().toLowerCase() + "@platform.example";
        User user = activeUser(1L, Set.of(role), username);

        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(accessTokenIssuer.issue(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user, null)).thenReturn("refresh-token");

        assertBearerPair(authService.login(new LoginRequest(username, PASSWORD)));
    }

    private void assertOrganizationLoginSucceeds(Role role) {
        UUID organizationId = UUID.randomUUID();
        String username = role.name().toLowerCase() + "@tenant.example";
        User user = organizationAccount(1L, organizationId, role, username);

        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(accessTokenIssuer.issue(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user, organizationId)).thenReturn("refresh-token");

        assertBearerPair(authService.login(new LoginRequest(username, PASSWORD)));
    }

    private void assertBearerPair(TokenResponse response) {
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.mustChangePassword()).isFalse();
    }

    @Test
    void login_platformAdmin_succeeds() {
        assertPlatformLoginSucceeds(Role.PLATFORM_ADMIN);
    }

    @Test
    void login_platformAuthor_succeeds() {
        assertPlatformLoginSucceeds(Role.PLATFORM_AUTHOR);
    }

    @Test
    void login_hostAdmin_succeeds() {
        assertOrganizationLoginSucceeds(Role.HOST_ADMIN);
    }

    @Test
    void login_examiner_succeeds() {
        assertOrganizationLoginSucceeds(Role.EXAMINER);
    }

    @Test
    void login_proctor_succeeds() {
        assertOrganizationLoginSucceeds(Role.PROCTOR);
    }

    @Test
    void login_student_succeeds() {
        assertOrganizationLoginSucceeds(Role.STUDENT);
    }

    @Test
    void login_studentWithTemporaryPassword_returnsMustChangePasswordFlag() {
        UUID organizationId = UUID.randomUUID();
        String username = "school.abcdefgh";
        User user = organizationAccount(1L, organizationId, Role.STUDENT, username);
        user.setMustChangePassword(true);
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(accessTokenIssuer.issue(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user, organizationId)).thenReturn("refresh-token");

        TokenResponse response = authService.login(new LoginRequest(username, PASSWORD));

        assertThat(response.mustChangePassword()).isTrue();
    }

    @Test
    void login_accountLandsInItsOwnOrganization_withoutAnyOrganizationChoice() {
        UUID organizationId = UUID.randomUUID();
        String username = "school.owner001";
        User user = organizationAccount(1L, organizationId, Role.HOST_ADMIN, username);
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(accessTokenIssuer.issue(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user, organizationId)).thenReturn("refresh-token");

        authService.login(new LoginRequest(username, PASSWORD));

        verify(accessTokenIssuer).issue(user);
        verify(refreshTokenService).issue(user, organizationId);
    }

    @Test
    void login_freePracticeStudentWithoutOrganization_getsATokenWithoutATenant() {
        String username = "free.student@example.test";
        User user = activeUser(1L, Set.of(Role.STUDENT), username);
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(accessTokenIssuer.issue(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user, null)).thenReturn("refresh-token");

        assertBearerPair(authService.login(new LoginRequest(username, PASSWORD)));
        verify(refreshTokenService).issue(user, null);
    }

    @Test
    void login_accountWithoutOrganizationAndWithoutRoles_isRejected() {
        String username = "stranded@example.test";
        User user = activeUser(1L, Set.of(), username);
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(username, PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
        verify(accessTokenIssuer, never()).issue(any());
    }

    @Test
    void login_unknownUsername_throwsInvalidLogin() {
        when(userRepository.findByUsernameAndDeletedFalse("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void login_wrongPassword_throwsInvalidLogin() {
        User user = activeUser(1L, Set.of(Role.PLATFORM_ADMIN), "admin@test.local");
        when(userRepository.findByUsernameAndDeletedFalse("admin@test.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin@test.local", "WrongPassword1")))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void login_suspendedUser_throwsInvalidLoginWithoutCheckingPassword() {
        User user = organizationAccount(1L, UUID.randomUUID(), Role.HOST_ADMIN, "host@tenant.example");
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByUsernameAndDeletedFalse("host@tenant.example")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("host@tenant.example", PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
        verify(accessTokenIssuer, never()).issue(any());
    }

    @Test
    void login_archivedUser_throwsInvalidLogin() {
        when(userRepository.findByUsernameAndDeletedFalse("archived@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("archived@example.test", PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void login_accountWithoutCredential_throwsInvalidLogin() {
        User user = organizationAccount(1L, UUID.randomUUID(), Role.STUDENT, "passwordless@example.test");
        user.setCredentialHash(null);
        when(userRepository.findByUsernameAndDeletedFalse("passwordless@example.test")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("passwordless@example.test", PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void refresh_archivedUser_throwsInvalidRefreshToken() {
        when(refreshTokenService.consume("refresh-token")).thenReturn(new RefreshSession(9L, null));
        when(userRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("refresh-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_staysInTheOrganizationOfTheAccount() {
        UUID organizationId = UUID.randomUUID();
        User user = organizationAccount(7L, organizationId, Role.HOST_ADMIN, "host@school.vn");
        when(refreshTokenService.consume("raw")).thenReturn(new RefreshSession(7L, organizationId));
        when(userRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(user));
        when(accessTokenIssuer.issue(user)).thenReturn("access-2");
        when(refreshTokenService.issue(user, organizationId)).thenReturn("refresh-2");

        TokenResponse response = authService.refresh(new RefreshRequest("raw"));

        assertThat(response.accessToken()).isEqualTo("access-2");
        assertThat(response.refreshToken()).isEqualTo("refresh-2");
    }

    @Test
    void refresh_platformAccountWithoutOrganization_staysWithoutOne() {
        User user = activeUser(8L, Set.of(Role.PLATFORM_ADMIN), "admin@platform.example");
        when(refreshTokenService.consume("raw")).thenReturn(new RefreshSession(8L, null));
        when(userRepository.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(user));
        when(accessTokenIssuer.issue(user)).thenReturn("access-2");
        when(refreshTokenService.issue(user, null)).thenReturn("refresh-2");

        assertThat(authService.refresh(new RefreshRequest("raw")).accessToken()).isEqualTo("access-2");
    }

    @Test
    void refresh_tokenBoundToAnotherOrganization_isRejected() {
        UUID organizationId = UUID.randomUUID();
        User user = organizationAccount(9L, organizationId, Role.STUDENT, "student@tenant.example");
        when(refreshTokenService.consume("refresh-token")).thenReturn(new RefreshSession(9L, UUID.randomUUID()));
        when(userRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("refresh-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(accessTokenIssuer, never()).issue(any());
    }

    @Test
    void refresh_suspendedAccount_isRejected() {
        UUID organizationId = UUID.randomUUID();
        User user = organizationAccount(9L, organizationId, Role.STUDENT, "suspended@tenant.example");
        user.suspend();
        when(refreshTokenService.consume("refresh-token")).thenReturn(new RefreshSession(9L, organizationId));
        when(userRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("refresh-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(accessTokenIssuer, never()).issue(any());
    }

    @Test
    void logout_revokesTheRefreshToken() {
        authService.logout(new RefreshRequest("refresh-token"));

        verify(refreshTokenService).revoke("refresh-token");
    }

    @Test
    void login_isRefusedWithoutCheckingThePasswordWhenTheAccountIsLocked() {
        String username = "locked@tenant.example";
        when(loginThrottle.isLocked(username, "203.0.113.9")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(username, PASSWORD), "203.0.113.9"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        verify(userRepository, never()).findByUsernameAndDeletedFalse(any());
    }

    @Test
    void login_wrongPasswordTakesOneFromTheFailureBudget() {
        String username = "host@tenant.example";
        User user = activeUser(1L, Set.of(Role.PLATFORM_ADMIN), username);
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(username, "wrong-password"), "203.0.113.9"))
                .isInstanceOf(InvalidLoginException.class);
        verify(loginThrottle).recordFailure(username, "203.0.113.9");
        verify(loginThrottle, never()).clear(any(), any());
    }

    @Test
    void login_unknownUserAlsoCountsAsAFailure() {
        when(userRepository.findByUsernameAndDeletedFalse("nobody@x.vn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@x.vn", PASSWORD), "203.0.113.9"))
                .isInstanceOf(InvalidLoginException.class);
        verify(loginThrottle).recordFailure("nobody@x.vn", "203.0.113.9");
    }

    @Test
    void login_correctPasswordClearsTheFailureBudget() {
        String username = "admin@platform.example";
        User user = activeUser(1L, Set.of(Role.PLATFORM_ADMIN), username);
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(accessTokenIssuer.issue(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user, null)).thenReturn("refresh-token");

        assertBearerPair(authService.login(new LoginRequest(username, PASSWORD), "203.0.113.9"));
        verify(loginThrottle).clear(username, "203.0.113.9");
        verify(loginThrottle, never()).recordFailure(any(), any());
    }
}
