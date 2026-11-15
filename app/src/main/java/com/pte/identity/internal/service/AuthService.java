package com.pte.identity.internal.service;

import com.pte.identity.domain.User;
import com.pte.identity.internal.config.AuthTokenProperties;
import com.pte.identity.internal.dto.request.auth.LoginRequest;
import com.pte.identity.internal.dto.request.auth.RefreshRequest;
import com.pte.identity.internal.dto.response.TokenResponse;
import com.pte.identity.internal.exception.InvalidLoginException;
import com.pte.identity.internal.exception.InvalidRefreshTokenException;
import com.pte.identity.internal.exception.TooManyLoginAttemptsException;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.AccessTokenIssuer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Login / refresh / logout. An account belongs to at most one organization, so the user name alone decides where a
 * login lands: the organization is baked into the access token and bound to the refresh token, so a token never
 * crosses organizations. Access is a signed RS256 JWT (stateless, validated in-process), refresh is an opaque
 * rotating token stored hashed.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenService refreshTokenService;
    private final LoginThrottle loginThrottle;
    private final AuthTokenProperties tokenProperties;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AccessTokenIssuer accessTokenIssuer, RefreshTokenService refreshTokenService,
                       LoginThrottle loginThrottle, AuthTokenProperties tokenProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenService = refreshTokenService;
        this.loginThrottle = loginThrottle;
        this.tokenProperties = tokenProperties;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        return login(request, null);
    }

    /** {@code clientIp} scopes the failure budget together with the user name; null counts as one unknown address. */
    @Transactional
    public TokenResponse login(LoginRequest request, String clientIp) {
        User user = authenticate(request.username(), request.password(), clientIp);
        if (user.getOrganizationId() == null && user.getRoles().isEmpty()) {
            throw new InvalidLoginException();
        }
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshSession session = refreshTokenService.consume(request.refreshToken());
        User user = userRepository.findByIdAndDeletedFalse(session.userId())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (user.isSuspended() || !Objects.equals(session.organizationId(), user.getOrganizationId())) {
            throw new InvalidRefreshTokenException();
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private User authenticate(String username, String password, String clientIp) {
        if (loginThrottle.isLocked(username, clientIp)) {
            throw new TooManyLoginAttemptsException();
        }
        User user = userRepository.findByUsernameAndDeletedFalse(username).orElse(null);
        if (user == null || user.isSuspended() || user.getCredentialHash() == null
                || !passwordEncoder.matches(password, user.getCredentialHash())) {
            loginThrottle.recordFailure(username, clientIp);
            throw new InvalidLoginException();
        }
        loginThrottle.clear(username, clientIp);
        return user;
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = accessTokenIssuer.issue(user);
        String refreshToken = refreshTokenService.issue(user, user.getOrganizationId());
        return TokenResponse.bearer(accessToken, refreshToken, tokenProperties.getAccessTokenTtlSeconds(),
                user.isMustChangePassword());
    }
}
