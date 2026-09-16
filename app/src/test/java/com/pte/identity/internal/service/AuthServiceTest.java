package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.domain.LoginHash;
import com.pte.identity.internal.dto.request.LoginRequest;
import com.pte.identity.internal.dto.response.TokenResponse;
import com.pte.identity.internal.exception.InvalidLoginException;
import com.pte.identity.internal.repository.LoginHashRepository;
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
import static org.mockito.Mockito.when;

/**
 * plans/quang-tenant-commercialization Phase 1 moved the login key from email
 * to username — one test per role (not a parameterized/looped test) so a
 * single role silently falling through the change is its own failure, not
 * hidden inside a shared assertion.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "Password123!";

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginHashRepository loginHashRepository;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    @Mock
    private RefreshTokenService refreshTokenService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, loginHashRepository, passwordEncoder,
                accessTokenIssuer, refreshTokenService);
    }

    private User activeUser(Long id, UUID tenantId, Role role, String username) {
        User user = new User();
        user.setId(id);
        user.setPublicId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username);
        user.setFullName("Test User");
        user.setTenantId(tenantId);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        return user;
    }

    private LoginHash hashFor(Long userId, String rawPassword) {
        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(userId);
        loginHash.setHash(passwordEncoder.encode(rawPassword));
        return loginHash;
    }

    private void assertLoginSucceeds(Role role, UUID tenantId) {
        Long userId = 1L;
        String username = role.name().toLowerCase() + "@tenant.example";
        User user = activeUser(userId, tenantId, role, username);
        LoginHash loginHash = hashFor(userId, PASSWORD);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(loginHashRepository.findByUserId(userId)).thenReturn(Optional.of(loginHash));
        when(accessTokenIssuer.issue(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");

        TokenResponse response = authService.login(new LoginRequest(username, PASSWORD));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.mustChangePassword()).isFalse();
    }

    @Test
    void login_platformAdmin_succeeds() {
        assertLoginSucceeds(Role.PLATFORM_ADMIN, null);
    }

    @Test
    void login_platformAuthor_succeeds() {
        assertLoginSucceeds(Role.PLATFORM_AUTHOR, null);
    }

    @Test
    void login_hostAdmin_succeeds() {
        assertLoginSucceeds(Role.HOST_ADMIN, UUID.randomUUID());
    }

    @Test
    void login_hostAuthor_succeeds() {
        assertLoginSucceeds(Role.HOST_AUTHOR, UUID.randomUUID());
    }

    @Test
    void login_proctor_succeeds() {
        assertLoginSucceeds(Role.PROCTOR, UUID.randomUUID());
    }

    @Test
    void login_student_succeeds() {
        assertLoginSucceeds(Role.STUDENT, UUID.randomUUID());
    }

    @Test
    void login_studentWithTemporaryPassword_returnsMustChangePasswordFlag() {
        UUID tenantId = UUID.randomUUID();
        String username = "school.abcdefgh";
        User user = activeUser(1L, tenantId, Role.STUDENT, username);
        user.setMustChangePassword(true);
        LoginHash loginHash = hashFor(1L, PASSWORD);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(loginHashRepository.findByUserId(1L)).thenReturn(Optional.of(loginHash));
        when(accessTokenIssuer.issue(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");

        TokenResponse response = authService.login(new LoginRequest(username, PASSWORD));

        assertThat(response.mustChangePassword()).isTrue();
    }

    @Test
    void login_lecturer_succeeds() {
        assertLoginSucceeds(Role.LECTURER, UUID.randomUUID());
    }

    @Test
    void login_programCoordinator_succeeds() {
        assertLoginSucceeds(Role.PROGRAM_COORDINATOR, UUID.randomUUID());
    }

    @Test
    void login_unknownUsername_throwsInvalidLogin() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void login_wrongPassword_throwsInvalidLogin() {
        User user = activeUser(1L, null, Role.PLATFORM_ADMIN, "admin@test.local");
        LoginHash loginHash = hashFor(1L, PASSWORD);
        when(userRepository.findByUsername("admin@test.local")).thenReturn(Optional.of(user));
        when(loginHashRepository.findByUserId(1L)).thenReturn(Optional.of(loginHash));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin@test.local", "WrongPassword1")))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void login_suspendedUser_throwsInvalidLoginWithoutCheckingPassword() {
        User user = activeUser(1L, UUID.randomUUID(), Role.HOST_ADMIN, "host@tenant.example");
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByUsername("host@tenant.example")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("host@tenant.example", PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
    }
}
