package com.pte.identity.internal.service.passwordreset;

import com.pte.identity.internal.service.RefreshTokenService;
import com.pte.identity.PasswordResetEmailRequestedEvent;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.config.PasswordResetProperties;
import com.pte.identity.internal.config.WebAppProperties;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.domain.PasswordResetToken;
import com.pte.identity.internal.exception.InvalidPasswordResetTokenException;
import com.pte.identity.internal.repository.PasswordResetTokenRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.TokenHasher;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Self-service password reset: everyone except Students asks by e-mail, gets a one-time link, and chooses a new
 * password. The caller can never tell from the response whether the e-mail belongs to an account.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String TENANT_URL = "https://host.example";
    private static final String ADMIN_URL = "https://admin.example";
    private static final String PRACTICE_URL = "https://practice.example";
    private static final String CLIENT_IP = "203.0.113.9";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PasswordResetThrottle throttle;

    private final TokenHasher tokenHasher = new TokenHasher();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, tokenHasher,
                passwordEncoder, refreshTokenService, auditLogService, eventPublisher, throttle,
                Clock.fixed(NOW, ZoneOffset.UTC), webProperties(), new PasswordResetProperties());
        lenient().when(throttle.tryAcquire(anyString(), anyString(), anyString())).thenReturn(true);
    }

    // ---- request ----

    @Test
    void request_unknownEmail_doesNothing() {
        when(userRepository.findByUsernameAndDeletedFalse("nobody@x.vn")).thenReturn(Optional.empty());

        service.request("nobody@x.vn", "nobody@x.vn", CLIENT_IP);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_hostAccount_storesOnlyAHashAndMailsATenantPortalLink() {
        User host = account(1L, "host@x.vn");
        withRole(host, Role.HOST_ADMIN);
        when(userRepository.findByUsernameAndDeletedFalse("host@x.vn")).thenReturn(Optional.of(host));

        service.request("host@x.vn", "host@x.vn", CLIENT_IP);

        PasswordResetEmailRequestedEvent event = capturedEvent();
        assertThat(event.email()).isEqualTo("host@x.vn");
        assertThat(event.links()).hasSize(1);
        String url = event.links().get(0).url();
        assertThat(url).startsWith(TENANT_URL + "/reset-password?token=");
        assertThat(event.links().get(0).username()).isEqualTo("host@x.vn");

        PasswordResetToken stored = capturedToken();
        String raw = url.substring(url.indexOf("token=") + "token=".length());
        assertThat(stored.getTokenHash()).isEqualTo(tokenHasher.hash(raw)).isNotEqualTo(raw);
        assertThat(stored.getUserId()).isEqualTo(1L);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(stored.getUsedAt()).isNull();
    }

    @Test
    void request_platformAccount_getsALinkToTheAdminPortal() {
        User admin = account(2L, "admin@x.vn");
        admin.setRoles(new HashSet<>(Set.of(Role.PLATFORM_ADMIN)));
        when(userRepository.findByUsernameAndDeletedFalse("admin@x.vn")).thenReturn(Optional.of(admin));

        service.request("admin@x.vn", "admin@x.vn", CLIENT_IP);

        assertThat(capturedEvent().links().get(0).url()).startsWith(ADMIN_URL + "/reset-password?token=");
    }

    @Test
    void request_studentAccount_linksToThePracticeApp() {
        User student = account(3L, "student@x.vn");
        withRole(student, Role.STUDENT);
        when(userRepository.findByUsernameAndDeletedFalse("student@x.vn")).thenReturn(Optional.of(student));

        service.request("student@x.vn", "student@x.vn", CLIENT_IP);

        assertThat(capturedEvent().links().get(0).url()).startsWith(PRACTICE_URL + "/reset-password?token=");
    }

    @Test
    void request_accountHoldingAStudentRoleAlongsideAnotherRole_linksToTheOrganizationPortal() {
        User both = account(4L, "both@x.vn");
        withRole(both, Role.PROCTOR);
        both.getRoles().add(Role.STUDENT);
        when(userRepository.findByUsernameAndDeletedFalse("both@x.vn")).thenReturn(Optional.of(both));

        service.request("both@x.vn", "both@x.vn", CLIENT_IP);

        assertThat(capturedEvent().links().get(0).url()).startsWith(TENANT_URL + "/reset-password?token=");
    }

    @Test
    void request_freePracticeStudentWithoutOrganization_linksToThePracticeApp() {
        User free = account(9L, "free@x.vn");
        free.setOrganizationId(null);
        free.setRoles(new HashSet<>(Set.of(Role.STUDENT)));
        when(userRepository.findByUsernameAndDeletedFalse("free@x.vn")).thenReturn(Optional.of(free));

        service.request("free@x.vn", "free@x.vn", CLIENT_IP);

        assertThat(capturedEvent().links().get(0).url()).startsWith(PRACTICE_URL + "/reset-password?token=");
    }

    @Test
    void request_accountWithoutAnyRole_isIgnored() {
        User roleless = account(10L, "none@x.vn");
        when(userRepository.findByUsernameAndDeletedFalse("none@x.vn")).thenReturn(Optional.of(roleless));

        service.request("none@x.vn", "none@x.vn", CLIENT_IP);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_suspendedAccount_isIgnored() {
        User suspended = account(5L, "gone@x.vn");
        suspended.setStatus(UserStatus.SUSPENDED);
        withRole(suspended, Role.HOST_ADMIN);
        when(userRepository.findByUsernameAndDeletedFalse("gone@x.vn")).thenReturn(Optional.of(suspended));

        service.request("gone@x.vn", "gone@x.vn", CLIENT_IP);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_invalidatesTheAccountsEarlierLinksBeforeIssuingANewOne() {
        User host = account(1L, "host@x.vn");
        withRole(host, Role.HOST_ADMIN);
        when(userRepository.findByUsernameAndDeletedFalse("host@x.vn")).thenReturn(Optional.of(host));

        service.request("host@x.vn", "host@x.vn", CLIENT_IP);

        verify(tokenRepository).invalidateActiveByUserId(1L, NOW);
    }

    @Test
    void request_normalisesTheEmailBeforeLookupAndThrottling() {
        when(userRepository.findByUsernameAndDeletedFalse("host-1")).thenReturn(Optional.empty());

        service.request("  Host@X.vn ", "host-1", CLIENT_IP);

        verify(throttle).tryAcquire("host@x.vn", "host-1", CLIENT_IP);
        verify(userRepository).findByUsernameAndDeletedFalse("host-1");
    }

    @Test
    void request_overTheLimit_doesNotEvenLookTheAccountUp() {
        when(throttle.tryAcquire("host@x.vn", "host-1", CLIENT_IP)).thenReturn(false);

        service.request("host@x.vn", "host-1", CLIENT_IP);

        verify(userRepository, never()).findByUsernameAndDeletedFalse(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_blankOrMissingUsername_doesNothingAndSpendsNoBudget() {
        service.request("host@x.vn", "", CLIENT_IP);
        service.request("host@x.vn", "   ", CLIENT_IP);
        service.request("host@x.vn", null, CLIENT_IP);

        verify(throttle, never()).tryAcquire(anyString(), anyString(), anyString());
        verify(userRepository, never()).findByUsernameAndDeletedFalse(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- request with a username ----

    @Test
    void request_usernameAndMatchingEmail_issuesOneTokenForThatAccountOnly() {
        User first = account(6L, "first");
        first.setEmail("shared@x.vn");
        User second = account(7L, "second");
        second.setEmail("shared@x.vn");
        withRole(first, Role.HOST_ADMIN);
        withRole(second, Role.EXAMINER);
        when(userRepository.findByUsernameAndDeletedFalse("first")).thenReturn(Optional.of(first));

        service.request("  Shared@X.vn ", " first ", CLIENT_IP);

        PasswordResetEmailRequestedEvent event = capturedEvent();
        assertThat(event.links()).extracting(PasswordResetEmailRequestedEvent.Link::username).containsExactly("first");
        assertThat(capturedToken().getUserId()).isEqualTo(6L);
        verify(tokenRepository, org.mockito.Mockito.times(1)).save(any());
        verify(throttle).tryAcquire("shared@x.vn", "first", CLIENT_IP);
    }

    @Test
    void request_storedEmailInMixedCaseWithSpaces_stillMatches() {
        User first = account(6L, "first");
        first.setEmail("  First.Last@X.vn ");
        withRole(first, Role.HOST_ADMIN);
        when(userRepository.findByUsernameAndDeletedFalse("first")).thenReturn(Optional.of(first));

        service.request("first.last@x.vn", "first", CLIENT_IP);

        assertThat(capturedEvent().links()).hasSize(1);
    }

    @Test
    void request_usernameInAnotherCase_isAnotherAccountAsAtLogin() {
        when(userRepository.findByUsernameAndDeletedFalse("First")).thenReturn(Optional.empty());

        service.request("first@x.vn", "First", CLIENT_IP);

        verify(userRepository).findByUsernameAndDeletedFalse("First");
        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_accountWithoutAnEmail_doesNothing() {
        User noEmail = account(8L, "first");
        noEmail.setEmail(null);
        withRole(noEmail, Role.HOST_ADMIN);
        when(userRepository.findByUsernameAndDeletedFalse("first")).thenReturn(Optional.of(noEmail));

        service.request("first@x.vn", "first", CLIENT_IP);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_usernameWithAnotherAccountsEmail_doesNothing() {
        User first = account(6L, "first");
        first.setEmail("first@x.vn");
        withRole(first, Role.HOST_ADMIN);
        when(userRepository.findByUsernameAndDeletedFalse("first")).thenReturn(Optional.of(first));

        service.request("other@x.vn", "first", CLIENT_IP);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_unknownUsername_doesNothing() {
        when(userRepository.findByUsernameAndDeletedFalse("nobody")).thenReturn(Optional.empty());

        service.request("host@x.vn", "nobody", CLIENT_IP);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_usernameOfASuspendedAccount_doesNothing() {
        User suspended = account(5L, "demo.gone");
        suspended.setEmail("s@x.vn");
        suspended.setStatus(UserStatus.SUSPENDED);
        withRole(suspended, Role.EXAMINER);
        when(userRepository.findByUsernameAndDeletedFalse("demo.gone")).thenReturn(Optional.of(suspended));

        service.request("s@x.vn", "demo.gone", CLIENT_IP);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_overTheUsernameBudget_doesNotEvenLookTheUsernameUp() {
        when(throttle.tryAcquire("host@x.vn", "host", CLIENT_IP)).thenReturn(false);

        service.request("host@x.vn", "host", CLIENT_IP);

        verify(userRepository, never()).findByUsernameAndDeletedFalse(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- confirm ----

    @Test
    void confirm_validToken_changesThePasswordAndSignsEverySessionOut() {
        User host = account(1L, "host@x.vn");
        host.setMustChangePassword(true);
        withRole(host, Role.HOST_ADMIN);
        PasswordResetToken token = tokenFor("raw-token", 1L, NOW.plusSeconds(60));
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(host));
        when(tokenRepository.markUsed(token.getId(), NOW)).thenReturn(1);

        service.confirm("raw-token", "NewPassword456");

        assertThat(passwordEncoder.matches("NewPassword456", host.getCredentialHash())).isTrue();
        assertThat(host.isMustChangePassword()).isFalse();
        verify(userRepository).save(host);
        verify(refreshTokenService).revokeAllForUser(1L);
        verify(auditLogService).record(argThat((CurrentUser actor) -> host.getPublicId().equals(actor.userId())),
                eq(IdentityConstants.USER_AGGREGATE), eq(host.getPublicId().toString()),
                eq(IdentityConstants.USER_PASSWORD_RESET_SELF), anyString());
    }

    @Test
    void confirm_unknownToken_isRejectedAndChangesNothing() {
        when(tokenRepository.findByTokenHashAndDeletedFalse(tokenHasher.hash("nope"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("nope", "NewPassword456"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void confirm_expiredToken_isRejectedAndChangesNothing() {
        tokenFor("old-token", 1L, NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.confirm("old-token", "NewPassword456"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verify(tokenRepository, never()).markUsed(anyLong(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirm_tokenAlreadyUsed_isRejectedAndChangesNothing() {
        PasswordResetToken token = tokenFor("used-token", 1L, NOW.plusSeconds(60));
        when(tokenRepository.markUsed(token.getId(), NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.confirm("used-token", "NewPassword456"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void confirm_accountSuspendedSinceTheLinkWasSent_isRejected() {
        User suspended = account(1L, "host@x.vn");
        suspended.setStatus(UserStatus.SUSPENDED);
        PasswordResetToken token = tokenFor("late-token", 1L, NOW.plusSeconds(60));
        when(tokenRepository.markUsed(token.getId(), NOW)).thenReturn(1);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.confirm("late-token", "NewPassword456"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verify(userRepository, never()).save(any());
    }

    // ---- isUsable: lets the page tell a spent or expired link before anyone types a password ----

    @Test
    void isUsable_aValidLink_isTrueAndChangesNothing() {
        User host = account(1L, "host@x.vn");
        withRole(host, Role.HOST_ADMIN);
        tokenFor("raw-token", 1L, NOW.plusSeconds(60));
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(host));

        assertThat(service.isUsable("raw-token")).isTrue();

        verify(tokenRepository, never()).markUsed(anyLong(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void isUsable_aLinkAlreadySpent_isFalse() {
        PasswordResetToken token = tokenFor("spent-token", 1L, NOW.plusSeconds(60));
        token.setUsedAt(NOW.minusSeconds(5));

        assertThat(service.isUsable("spent-token")).isFalse();
    }

    @Test
    void isUsable_anExpiredLink_isFalse() {
        tokenFor("old-token", 1L, NOW.minusSeconds(1));

        assertThat(service.isUsable("old-token")).isFalse();
    }

    @Test
    void isUsable_anUnknownLink_isFalse() {
        when(tokenRepository.findByTokenHashAndDeletedFalse(tokenHasher.hash("nope"))).thenReturn(Optional.empty());

        assertThat(service.isUsable("nope")).isFalse();
    }

    @Test
    void isUsable_whenTheAccountCanNoLongerSignIn_isFalse() {
        User suspended = account(1L, "host@x.vn");
        suspended.setStatus(UserStatus.SUSPENDED);
        tokenFor("late-token", 1L, NOW.plusSeconds(60));
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(suspended));

        assertThat(service.isUsable("late-token")).isFalse();
    }

    @Test
    void isUsable_aBlankOrMissingToken_isFalseWithoutLookingAnythingUp() {
        assertThat(service.isUsable("   ")).isFalse();
        assertThat(service.isUsable(null)).isFalse();

        verify(tokenRepository, never()).findByTokenHashAndDeletedFalse(anyString());
    }

    // ---- helpers ----

    private static WebAppProperties webProperties() {
        WebAppProperties properties = new WebAppProperties();
        properties.setTenantUrl(TENANT_URL);
        properties.setAdminUrl(ADMIN_URL);
        properties.setPracticeUrl(PRACTICE_URL);
        return properties;
    }

    private PasswordResetEmailRequestedEvent capturedEvent() {
        ArgumentCaptor<PasswordResetEmailRequestedEvent> captor =
                ArgumentCaptor.forClass(PasswordResetEmailRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private PasswordResetToken capturedToken() {
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        return captor.getValue();
    }

    private PasswordResetToken tokenFor(String raw, Long userId, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setId(70L);
        token.setUserId(userId);
        token.setTokenHash(tokenHasher.hash(raw));
        token.setExpiresAt(expiresAt);
        when(tokenRepository.findByTokenHashAndDeletedFalse(tokenHasher.hash(raw))).thenReturn(Optional.of(token));
        return token;
    }

    /** Gives the account its one role; a platform role leaves it outside every organization. */
    private void withRole(User user, Role role) {
        user.setRoles(new HashSet<>(Set.of(role)));
        user.setOrganizationId(SecurityRoles.isPlatformRole(role.name()) ? null : UUID.randomUUID());
    }

    private User account(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setPublicId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username.contains("@") ? username : username + "@x.vn");
        user.setStatus(UserStatus.ACTIVE);
        user.setCredentialHash(passwordEncoder.encode("OldPassword123"));
        return user;
    }
}
