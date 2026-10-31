package com.pte.identity.internal.service;

import com.pte.identity.PasswordResetEmailRequestedEvent;
import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.domain.PasswordResetToken;
import com.pte.identity.internal.exception.InvalidPasswordResetTokenException;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.PasswordResetTokenRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.TokenHasher;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import org.springframework.beans.factory.annotation.Value;
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
 * reveals whether the address belongs to an account: it simply returns, and a mail follows only when it does.
 */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final String RESET_PATH = "/reset-password?token=";

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
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

    public PasswordResetService(UserRepository userRepository, OrganizationMemberRepository memberRepository,
            PasswordResetTokenRepository tokenRepository, TokenHasher tokenHasher, PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService, AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher, PasswordResetThrottle throttle, Clock clock,
            @Value("${app.web.tenant-url:http://localhost:3000}") String tenantUrl, @Value("${app.web.admin-url:http://localhost:3001}") String adminUrl,
            @Value("${app.password-reset.token-ttl-minutes:30}") long tokenTtlMinutes) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.tokenRepository = tokenRepository;
        this.tokenHasher = tokenHasher;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.throttle = throttle;
        this.clock = clock;
        this.tenantUrl = stripTrailingSlash(tenantUrl);
        this.adminUrl = stripTrailingSlash(adminUrl);
        this.tokenTtl = Duration.ofMinutes(tokenTtlMinutes);
    }

    @Transactional
    public void request(String email, String clientIp) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || !throttle.tryAcquire(normalized, clientIp)) {
            return;
        }
        List<PasswordResetEmailRequestedEvent.Link> links = new ArrayList<>();
        for (User user : userRepository.findAllByEmailIgnoreCaseAndDeletedFalse(normalized)) {
            Set<Role> roles = rolesOf(user);
            if (isEligible(user, roles)) {
                links.add(issueLink(user, roles));
            }
        }
        if (!links.isEmpty()) {
            eventPublisher.publishEvent(new PasswordResetEmailRequestedEvent(UUID.randomUUID(), normalized, links));
        }
    }

    @Transactional
    public void confirm(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHasher.hash(rawToken))
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
        return new PasswordResetEmailRequestedEvent.Link(user.getUsername(), base + RESET_PATH + raw);
    }

    /** An account may reset by e-mail when it can sign in, holds a role, and holds no Student role anywhere. */
    private boolean isEligible(User user, Set<Role> roles) {
        return !user.isSuspended() && user.getCredentialHash() != null && !roles.isEmpty()
                && !roles.contains(Role.STUDENT);
    }

    private Set<Role> rolesOf(User user) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        roles.addAll(user.getRoles());
        for (OrganizationMember member : memberRepository.findByUser_IdAndDeletedFalse(user.getId())) {
            roles.addAll(member.getRoles());
        }
        return roles;
    }

    private CurrentUser actorOf(User user) {
        return new CurrentUser(user.getPublicId(), null, rolesOf(user).stream().map(Role::name).toList());
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
