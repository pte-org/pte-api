package com.pte.identity.internal.service.passwordreset;

import com.pte.identity.internal.service.RefreshTokenService;
import com.pte.identity.PasswordResetEmailRequestedEvent;
import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.config.PasswordResetProperties;
import com.pte.identity.internal.config.WebAppProperties;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.domain.PasswordResetToken;
import com.pte.identity.internal.exception.InvalidPasswordResetTokenException;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.PasswordResetTokenRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.TokenHasher;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
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
    private static final String CLIENT_IP = "203.0.113.9";

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationMemberRepository memberRepository;
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
        service = new PasswordResetService(userRepository, memberRepository, tokenRepository, tokenHasher,
                passwordEncoder, refreshTokenService, auditLogService, eventPublisher, throttle,
                Clock.fixed(NOW, ZoneOffset.UTC), webProperties(), new PasswordResetProperties());
        lenient().when(throttle.tryAcquire(anyString(), anyString())).thenReturn(true);
    }

    // ---- request ----

    @Test
    void request_unknownEmail_doesNothing() {
        when(userRepository.findAllByEmailIgnoreCaseAndDeletedFalse("nobody@x.vn")).thenReturn(List.of());

        service.request("nobody@x.vn", CLIENT_IP);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_hostAccount_storesOnlyAHashAndMailsATenantPortalLink() {
        User host = account(1L, "host@x.vn");
        stubMember(host, Role.HOST_ADMIN);
        when(userRepository.findAllByEmailIgnoreCaseAndDeletedFalse("host@x.vn")).thenReturn(List.of(host));

        service.request("host@x.vn", CLIENT_IP);

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
        when(userRepository.findAllByEmailIgnoreCaseAndDeletedFalse("admin@x.vn")).thenReturn(List.of(admin));
        when(memberRepository.findByUser_IdAndDeletedFalse(2L)).thenReturn(List.of());

        service.request("admin@x.vn", CLIENT_IP);

        assertThat(capturedEvent().links().get(0).url()).startsWith(ADMIN_URL + "/reset-password?token=");
    }

    @Test
    void request_studentAccount_isIgnored() {
        User student = account(3L, "student@x.vn");
        stubMember(student, Role.STUDENT);
        when(userRepository.findAllByEmailIgnoreCaseAndDeletedFalse("student@x.vn")).thenReturn(List.of(student));

        service.request("student@x.vn", CLIENT_IP);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_accountThatIsAlsoAStudentInAnotherOrganization_isIgnored() {
        User both = account(4L, "both@x.vn");
        OrganizationMember asProctor = memberOf(both, Role.PROCTOR);
        OrganizationMember asStudent = memberOf(both, Role.STUDENT);
        when(memberRepository.findByUser_IdAndDeletedFalse(4L)).thenReturn(List.of(asProctor, asStudent));
        when(userRepository.findAllByEmailIgnoreCaseAndDeletedFalse("both@x.vn")).thenReturn(List.of(both));

        service.request("both@x.vn", CLIENT_IP);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_suspendedAccount_isIgnored() {
        User suspended = account(5L, "gone@x.vn");
        suspended.setStatus(UserStatus.SUSPENDED);
        lenient().when(memberRepository.findByUser_IdAndDeletedFalse(5L))
                .thenReturn(List.of(memberOf(suspended, Role.HOST_ADMIN)));
        when(userRepository.findAllByEmailIgnoreCaseAndDeletedFalse("gone@x.vn")).thenReturn(List.of(suspended));

        service.request("gone@x.vn", CLIENT_IP);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void request_emailSharedByTwoAccounts_sendsOneMailWithALinkPerAccount() {
        User first = account(6L, "first");
        first.setEmail("shared@x.vn");
        User second = account(7L, "second");
        second.setEmail("shared@x.vn");
        stubMember(first, Role.HOST_ADMIN);
        stubMember(second, Role.EXAMINER);
        when(userRepository.findAllByEmailIgnoreCaseAndDeletedFalse("shared@x.vn"))
                .thenReturn(List.of(first, second));

        service.request("shared@x.vn", CLIENT_IP);

        PasswordResetEmailRequestedEvent event = capturedEvent();
        assertThat(event.links()).extracting(PasswordResetEmailRequestedEvent.Link::username)
                .containsExactly("first", "second");
        assertThat(event.links().get(0).url()).isNotEqualTo(event.links().get(1).url());
    }

    @Test
    void request_invalidatesTheAccountsEarlierLinksBeforeIssuingANewOne() {
        User host = account(1L, "host@x.vn");
        stubMember(host, Role.HOST_ADMIN);
        when(userRepository.findAllByEmailIgnoreCaseAndDeletedFalse("host@x.vn")).thenReturn(List.of(host));

        service.request("host@x.vn", CLIENT_IP);

        verify(tokenRepository).invalidateActiveByUserId(1L, NOW);
    }

    @Test
    void request_normalisesTheEmailBeforeLookupAndThrottling() {
        when(userRepository.findAllByEmailIgnoreCaseAndDeletedFalse("host@x.vn")).thenReturn(List.of());

        service.request("  Host@X.vn ", CLIENT_IP);

        verify(throttle).tryAcquire("host@x.vn", CLIENT_IP);
        verify(userRepository).findAllByEmailIgnoreCaseAndDeletedFalse("host@x.vn");
    }

    @Test
    void request_overTheLimit_doesNotEvenLookTheEmailUp() {
        when(throttle.tryAcquire("host@x.vn", CLIENT_IP)).thenReturn(false);

        service.request("host@x.vn", CLIENT_IP);

        verify(userRepository, never()).findAllByEmailIgnoreCaseAndDeletedFalse(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- confirm ----

    @Test
    void confirm_validToken_changesThePasswordAndSignsEverySessionOut() {
        User host = account(1L, "host@x.vn");
        host.setMustChangePassword(true);
        stubMember(host, Role.HOST_ADMIN);
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
        stubMember(host, Role.HOST_ADMIN);
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

    private void stubMember(User user, Role role) {
        lenient().when(memberRepository.findByUser_IdAndDeletedFalse(user.getId()))
                .thenReturn(List.of(memberOf(user, role)));
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

    private OrganizationMember memberOf(User user, Role role) {
        OrganizationMember member = new OrganizationMember();
        member.setUser(user);
        member.setOrganizationId(UUID.randomUUID());
        member.setStatus(UserStatus.ACTIVE);
        member.setRoles(new HashSet<>(Set.of(role)));
        return member;
    }
}
