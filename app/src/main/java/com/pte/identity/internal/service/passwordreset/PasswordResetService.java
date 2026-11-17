package com.pte.identity.internal.service.passwordreset;

import com.pte.identity.internal.service.RefreshTokenService;
import com.pte.identity.PasswordResetEmailRequestedEvent;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Self-service password reset for everyone except Students (whose organization resets for them). A request never
 * reveals whether the username and address belong to an account: it simply returns, and a mail follows only when
 * they do.
 */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final TokenHasher tokenHasher;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordResetThrottle throttle;
    private final Clock clock;
    private final String tenantUrl;
    private final String adminUrl;
    private final Duration tokenTtl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository, TokenHasher tokenHasher, PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService, AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher, PasswordResetThrottle throttle, Clock clock,
            WebAppProperties webProperties, PasswordResetProperties properties) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenHasher = tokenHasher;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.throttle = throttle;
        this.clock = clock;
        this.tenantUrl = stripTrailingSlash(webProperties.getTenantUrl());
        this.adminUrl = stripTrailingSlash(webProperties.getAdminUrl());
        this.tokenTtl = Duration.ofMinutes(properties.getTokenTtlMinutes());
    }

    /**
     * Sends a reset link to the one account named by {@code username}, and only when {@code email} is that
     * account's e-mail. A blank e-mail or username returns without doing anything.
     */
    @Transactional
    public void request(String email, String username, String clientIp) {
        String typedEmail = email == null ? "" : email.trim();
        String normalized = typedEmail.toLowerCase(Locale.ROOT);
        String account = username == null ? "" : username.trim();
        if (normalized.isEmpty() || account.isEmpty() || !throttle.tryAcquire(normalized, account, clientIp)) {
            return;
        }
        userRepository.findByUsernameAndDeletedFalse(account)
                .filter(found -> found.getEmail() != null && typedEmail.equalsIgnoreCase(found.getEmail().trim()))
                .ifPresent(user -> {
                    Set<Role> roles = rolesOf(user);
                    if (isEligible(user, roles)) {
                        PasswordResetEmailRequestedEvent.Link link = issueLink(user, roles);
                        eventPublisher.publishEvent(new PasswordResetEmailRequestedEvent(UUID.randomUUID(), normalized,
                                List.of(link), (int) tokenTtl.toMinutes()));
                    }
                });
    }

    /**
     * Whether a link can still be used, without spending it: lets the page say so as soon as it opens instead of
     * after someone has typed a new password. A spent, expired, unknown or orphaned link all answer the same.
     */
    @Transactional(readOnly = true)
    public boolean isUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        Instant now = clock.instant();
        return tokenRepository.findByTokenHashAndDeletedFalse(tokenHasher.hash(rawToken))
                .filter(found -> found.getUsedAt() == null && found.getExpiresAt().isAfter(now))
                .flatMap(found -> userRepository.findByIdAndDeletedFalse(found.getUserId()))
                .filter(found -> isEligible(found, rolesOf(found)))
                .isPresent();
    }

    @Transactional
    public void confirm(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHashAndDeletedFalse(tokenHasher.hash(rawToken))
                .filter(found -> found.getUsedAt() == null && found.getExpiresAt().isAfter(now))
                .orElseThrow(InvalidPasswordResetTokenException::new);
        if (tokenRepository.markUsed(token.getId(), now) != 1) {
            throw new InvalidPasswordResetTokenException();
        }
        User user = userRepository.findByIdAndDeletedFalse(token.getUserId())
                .filter(found -> isEligible(found, rolesOf(found)))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        user.setCredentialHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        auditLogService.record(actorOf(user), IdentityConstants.USER_AGGREGATE, user.getPublicId().toString(),
                IdentityConstants.USER_PASSWORD_RESET_SELF, IdentityConstants.USER_PASSWORD_RESET_SELF_SUMMARY);
    }

    private PasswordResetEmailRequestedEvent.Link issueLink(User user, Set<Role> roles) {
        Instant now = clock.instant();
        tokenRepository.invalidateActiveByUserId(user.getId(), now);
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(tokenHasher.hash(raw));
        token.setExpiresAt(now.plus(tokenTtl));
        tokenRepository.save(token);

        String base = roles.stream().map(Role::name).anyMatch(SecurityRoles::isPlatformRole) ? adminUrl : tenantUrl;
        return new PasswordResetEmailRequestedEvent.Link(user.getUsername(), base + IdentityConstants.PASSWORD_RESET_PATH + raw);
    }

    /** An account may reset by e-mail when it can sign in, holds a role, and holds no Student role anywhere. */
    private boolean isEligible(User user, Set<Role> roles) {
        return !user.isSuspended() && user.getCredentialHash() != null && !roles.isEmpty()
                && !roles.contains(Role.STUDENT);
    }

    private Set<Role> rolesOf(User user) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        roles.addAll(user.getRoles());
        return roles;
    }

    private CurrentUser actorOf(User user) {
        return new CurrentUser(user.getPublicId(), null, rolesOf(user).stream().map(Role::name).toList());
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
