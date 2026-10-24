package com.pte.identity;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.exception.InvalidLoginException;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.AccessTokenIssuer;
import com.pte.identity.internal.service.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Public boundary for issuing the canonical bearer pair after verified identity auth. */
@Service
public class IdentityTokenService {

    private final UserRepository userRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenService refreshTokenService;

    public IdentityTokenService(UserRepository userRepository, AccessTokenIssuer accessTokenIssuer,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenService = refreshTokenService;
    }

    /** Issues an organization-less token for a verified practice account (its own STUDENT role). */
    @Transactional
    public IdentityTokenResponse issueForVerifiedUser(UUID userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .filter(candidate -> !candidate.isDeleted() && !candidate.isSuspended())
                .filter(candidate -> candidate.getRoles().contains(Role.STUDENT))
                .orElseThrow(InvalidLoginException::new);
        String accessToken = accessTokenIssuer.issue(user, null);
        String refreshToken = refreshTokenService.issue(user, null);
        return new IdentityTokenResponse(accessToken, refreshToken, "Bearer",
                IdentityConstants.ACCESS_TOKEN_TTL_SECONDS, user.isMustChangePassword());
    }
}
