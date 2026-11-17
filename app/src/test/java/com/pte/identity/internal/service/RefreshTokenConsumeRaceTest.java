package com.pte.identity.internal.service;

import com.pte.identity.domain.User;
import com.pte.identity.internal.config.AuthTokenProperties;
import com.pte.identity.internal.exception.InvalidRefreshTokenException;
import com.pte.identity.internal.repository.RefreshTokenRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.TokenHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A refresh token is single-use even when another request revoked it after this one had already read it. */
@DataJpaTest
@EnableConfigurationProperties(AuthTokenProperties.class)
@Import({RefreshTokenService.class, TokenHasher.class, com.pte.shared.config.ClockConfig.class})
class RefreshTokenConsumeRaceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void aTokenRevokedBehindOurBackAfterBeingReadCannotBeConsumedAgain() {
        User user = new User();
        user.setUsername("race@x.vn");
        user.setEmail("race@x.vn");
        user.setPublicId(java.util.UUID.randomUUID());
        user.setCredentialHash("hash");
        user = userRepository.saveAndFlush(user);
        String raw = refreshTokenService.issue(user, null);
        refreshTokenRepository.flush();

        // The other request commits its revocation with a bulk update; this request's loaded copy still says active.
        assertThat(refreshTokenRepository.revokeAllActiveByUserId(user.getId())).isEqualTo(1);

        assertThatThrownBy(() -> refreshTokenService.consume(raw)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void anActiveTokenIsConsumedExactlyOnce() {
        User user = new User();
        user.setUsername("once@x.vn");
        user.setEmail("once@x.vn");
        user.setPublicId(java.util.UUID.randomUUID());
        user.setCredentialHash("hash");
        user = userRepository.saveAndFlush(user);
        String raw = refreshTokenService.issue(user, null);

        assertThat(refreshTokenService.consume(raw).userId()).isEqualTo(user.getId());
        assertThatThrownBy(() -> refreshTokenService.consume(raw)).isInstanceOf(InvalidRefreshTokenException.class);
    }
}
