package com.pte.iam.service;

import com.pte.iam.constant.IamConstants;
import com.pte.iam.domain.LoginHash;
import com.pte.iam.domain.User;
import com.pte.iam.domain.exception.InvalidLoginException;
import com.pte.iam.domain.exception.InvalidRefreshTokenException;
import com.pte.iam.dto.request.LoginRequest;
import com.pte.iam.dto.request.RefreshRequest;
import com.pte.iam.dto.response.TokenResponse;
import com.pte.iam.repository.LoginHashRepository;
import com.pte.iam.repository.UserRepository;
import com.pte.iam.security.AccessTokenIssuer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login / refresh / logout. Emits access + refresh tokens; access is a signed
 * RS256 JWT (stateless, validated everywhere via JWKS), refresh is an opaque
 * rotating token stored hashed.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final LoginHashRepository loginHashRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, LoginHashRepository loginHashRepository,
                       PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokenIssuer,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.loginHashRepository = loginHashRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidLoginException::new);
        if (user.isSuspended()) {
            throw new InvalidLoginException();
        }
        LoginHash loginHash = loginHashRepository.findByUserId(user.getId())
                .orElseThrow(InvalidLoginException::new);
        if (!passwordEncoder.matches(request.password(), loginHash.getHash())) {
            throw new InvalidLoginException();
        }
        return issueTokens(user);
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
        return TokenResponse.bearer(accessToken, refreshToken, IamConstants.ACCESS_TOKEN_TTL_SECONDS);
    }
}
