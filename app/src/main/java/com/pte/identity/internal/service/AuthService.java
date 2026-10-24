package com.pte.identity.internal.service;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.LoginOrganizationOptionsRequest;
import com.pte.identity.internal.dto.request.LoginRequest;
import com.pte.identity.internal.dto.request.RefreshRequest;
import com.pte.identity.internal.dto.response.LoginOrganizationOptionResponse;
import com.pte.identity.internal.dto.response.TokenResponse;
import com.pte.identity.internal.exception.InvalidLoginException;
import com.pte.identity.internal.exception.InvalidRefreshTokenException;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
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
 * Login / refresh / logout. One account can belong to several organizations: the organization chosen at login is
 * baked into the access token and bound to the refresh token, so a token never crosses organizations. Access is a
 * signed RS256 JWT (stateless, validated in-process), refresh is an opaque rotating token stored hashed.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenService refreshTokenService;
    private final TenancyService tenancyService;

    public AuthService(UserRepository userRepository, OrganizationMemberRepository memberRepository,
                       PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokenIssuer,
                       RefreshTokenService refreshTokenService, TenancyService tenancyService) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenService = refreshTokenService;
        this.tenancyService = tenancyService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = authenticate(request.username(), request.password());
        return issueTokens(user, resolveMember(user, request.tenantId()));
    }

    /**
     * Verifies the supplied credentials before returning organization choices. This avoids exposing which
     * organizations an account belongs to for unauthenticated callers while still letting the UI disambiguate.
     */
    @Transactional(readOnly = true)
    public List<LoginOrganizationOptionResponse> loginOrganizations(LoginOrganizationOptionsRequest request) {
        User user = authenticate(request.username(), request.password());
        List<LoginOrganizationOptionResponse> options = new ArrayList<>();
        Set<UUID> seenOrganizationIds = new HashSet<>();
        for (OrganizationMember member : memberRepository.findByUser_IdAndDeletedFalse(user.getId())) {
            if (!member.isActive() || !seenOrganizationIds.add(member.getOrganizationId())) {
                continue;
            }
            tenancyService.findLoginOrganization(member.getOrganizationId())
                    .map(this::toResponse)
                    .ifPresent(options::add);
        }
        return options;
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshSession session = refreshTokenService.consume(request.refreshToken());
        User user = userRepository.findByIdAndDeletedFalse(session.userId())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (user.isSuspended()) {
            throw new InvalidRefreshTokenException();
        }
        OrganizationMember member = null;
        if (session.organizationId() != null) {
            member = memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(
                    user.getId(), session.organizationId())
                    .filter(OrganizationMember::isActive).orElseThrow(InvalidRefreshTokenException::new);
        }
        return issueTokens(user, member);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private User authenticate(String username, String password) {
        User user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(InvalidLoginException::new);
        if (user.isSuspended() || user.getCredentialHash() == null
                || !passwordEncoder.matches(password, user.getCredentialHash())) {
            throw new InvalidLoginException();
        }
        return user;
    }

    /**
     * The membership a login signs in to: the requested organization, or the only one the account has. An account
     * with several organizations must pick one (the client lists them through login options first). An account
     * with no organization signs in with its own roles (platform or practice-only).
     */
    private OrganizationMember resolveMember(User user, UUID requestedOrganizationId) {
        if (requestedOrganizationId != null) {
            return memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(user.getId(), requestedOrganizationId)
                    .filter(OrganizationMember::isActive).orElseThrow(InvalidLoginException::new);
        }
        List<OrganizationMember> members = memberRepository.findByUser_IdAndDeletedFalse(user.getId()).stream()
                .filter(OrganizationMember::isActive).toList();
        if (members.size() > 1) {
            throw new InvalidLoginException();
        }
        if (members.isEmpty()) {
            if (user.getRoles().isEmpty()) {
                throw new InvalidLoginException();
            }
            return null;
        }
        return members.get(0);
    }

    private LoginOrganizationOptionResponse toResponse(LoginOrganizationOption option) {
        return new LoginOrganizationOptionResponse(
                option.tenantId(), option.tenantCode(), option.organizationName(), option.organizationType());
    }

    private TokenResponse issueTokens(User user, OrganizationMember member) {
        UUID organizationId = member == null ? null : member.getOrganizationId();
        String accessToken = accessTokenIssuer.issue(user, member);
        String refreshToken = refreshTokenService.issue(user, organizationId);
        return TokenResponse.bearer(accessToken, refreshToken, IdentityConstants.ACCESS_TOKEN_TTL_SECONDS,
                user.isMustChangePassword());
    }
}
