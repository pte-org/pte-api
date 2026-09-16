package com.pte.identity.internal.service;

import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.domain.RefreshToken;
import com.pte.identity.internal.exception.InvalidRefreshTokenException;
import com.pte.identity.internal.repository.RefreshTokenRepository;
import com.pte.identity.internal.security.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Issues, rotates, and revokes refresh tokens. Only the SHA-256 hash is stored;
 * the raw token is returned once. Rotation (consume) revokes the presented token
 * so a stolen-and-replayed refresh token is single-use.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, TokenHasher tokenHasher) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
    }

    @Transactional
    public String issue(User user) {
        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(tokenHasher.hash(raw));
        token.setExpiresAt(Instant.now().plusSeconds(IdentityConstants.REFRESH_TOKEN_TTL_SECONDS));
        token.setRevoked(false);
        refreshTokenRepository.save(token);
        return raw;
    }

    @Transactional
    public Long consume(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!token.isActive(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }
        token.setRevoked(true);
        return token.getUserId();
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(tokenHasher.hash(rawToken))
                .ifPresent(token -> token.setRevoked(true));
    }
}
