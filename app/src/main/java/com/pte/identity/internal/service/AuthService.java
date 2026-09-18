package com.pte.identity.internal.service;

import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.domain.LoginHash;
import com.pte.identity.internal.dto.request.LoginRequest;
import com.pte.identity.internal.dto.request.LoginOrganizationOptionsRequest;
import com.pte.identity.internal.dto.request.RefreshRequest;
import com.pte.identity.internal.dto.response.LoginOrganizationOptionResponse;
import com.pte.identity.internal.dto.response.TokenResponse;
import com.pte.identity.internal.exception.InvalidLoginException;
import com.pte.identity.internal.exception.InvalidRefreshTokenException;
import com.pte.identity.internal.repository.LoginHashRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.AccessTokenIssuer;
import com.pte.tenancy.LoginOrganizationOption;
import com.pte.tenancy.TenancyService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Login / refresh / logout. Emits access + refresh tokens; access is a signed
 * RS256 JWT (stateless, validated in-process), refresh is an opaque rotating
 * token stored hashed.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final LoginHashRepository loginHashRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenService refreshTokenService;
    private final TenancyService tenancyService;

    public AuthService(UserRepository userRepository, LoginHashRepository loginHashRepository,
                       PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokenIssuer,
                       RefreshTokenService refreshTokenService, TenancyService tenancyService) {
        this.userRepository = userRepository;
        this.loginHashRepository = loginHashRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenService = refreshTokenService;
        this.tenancyService = tenancyService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = resolveUser(request);
        validateCredentials(user, request.password());
        return issueTokens(user);
    }

    /**
     * Verifies the supplied credentials before returning tenant choices. This
     * avoids exposing which organizations contain an email to unauthenticated
     * callers while still allowing the UI to disambiguate duplicate logins.
     */
    @Transactional(readOnly = true)
    public List<LoginOrganizationOptionResponse> loginOrganizations(LoginOrganizationOptionsRequest request) {
        List<LoginOrganizationOptionResponse> options = new ArrayList<>();
        Set<UUID> seenTenantIds = new HashSet<>();
        boolean credentialMatched = false;

        for (User user : userRepository.findByUsername(request.username())) {
            if (user.isSuspended()) {
                continue;
            }
            LoginHash loginHash = loginHashRepository.findByUserId(user.getId())
                    .orElse(null);
            if (loginHash == null || !passwordEncoder.matches(request.password(), loginHash.getHash())) {
                continue;
            }
            credentialMatched = true;
            if (user.getTenantId() == null || !seenTenantIds.add(user.getTenantId())) {
                continue;
            }
            tenancyService.findLoginOrganization(user.getTenantId())
                    .map(this::toResponse)
                    .ifPresent(options::add);
        }

        if (!credentialMatched) {
            throw new InvalidLoginException();
        }
        return options;
    }

    private User resolveUser(LoginRequest request) {
        if (request.tenantId() != null) {
            return userRepository.findByUsernameAndTenantId(request.username(), request.tenantId())
                    .orElseThrow(InvalidLoginException::new);
        }
        List<User> matches = userRepository.findByUsername(request.username());
        if (matches.size() != 1) {
            throw new InvalidLoginException();
        }
        return matches.get(0);
    }

    private void validateCredentials(User user, String password) {
        if (user.isSuspended()) {
            throw new InvalidLoginException();
        }
        LoginHash loginHash = loginHashRepository.findByUserId(user.getId())
                .orElseThrow(InvalidLoginException::new);
        if (!passwordEncoder.matches(password, loginHash.getHash())) {
            throw new InvalidLoginException();
        }
    }

    private LoginOrganizationOptionResponse toResponse(LoginOrganizationOption option) {
        return new LoginOrganizationOptionResponse(
                option.tenantId(), option.tenantCode(), option.organizationName(), option.organizationType());
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Long userId = refreshTokenService.consume(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (user.isSuspended()) {
            throw new InvalidRefreshTokenException();
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = accessTokenIssuer.issue(user);
        String refreshToken = refreshTokenService.issue(user);
        return TokenResponse.bearer(accessToken, refreshToken, IdentityConstants.ACCESS_TOKEN_TTL_SECONDS,
                user.isMustChangePassword());
    }
}
