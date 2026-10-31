package com.pte.identity.internal.service;

import com.pte.identity.domain.User;
import com.pte.identity.internal.exception.InvalidRefreshTokenException;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.TokenHasher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Changing one's own password signs every other device out but keeps the session that made the change. */
@DataJpaTest
@Import({RefreshTokenService.class, TokenHasher.class, com.pte.shared.config.ClockConfig.class})
class RefreshTokenRevokeOthersTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void keepsTheGivenTokenAndRevokesTheUsersOtherTokens() {
        User user = user("keep@x.vn");
        UUID organizationId = UUID.randomUUID();
        String kept = refreshTokenService.issue(user, organizationId);
        String otherDevice = refreshTokenService.issue(user, organizationId);
        String platformDevice = refreshTokenService.issue(user, null);

        refreshTokenService.revokeAllOthers(user.getId(), kept);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> refreshTokenService.consume(otherDevice))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refreshTokenService.consume(platformDevice))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(refreshTokenService.consume(kept).userId()).isEqualTo(user.getId());
    }

    @Test
    void revokesEverythingWhenTheKeptTokenBelongsToAnotherUser() {
        User user = user("owner@x.vn");
        User stranger = user("stranger@x.vn");
        String own = refreshTokenService.issue(user, null);
        String strangers = refreshTokenService.issue(stranger, null);

        refreshTokenService.revokeAllOthers(user.getId(), strangers);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> refreshTokenService.consume(own)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(refreshTokenService.consume(strangers).userId()).isEqualTo(stranger.getId());
    }

    @Test
    void revokesEverythingWhenTheKeptTokenIsUnknownOrBlank() {
        User user = user("unknown@x.vn");
        String first = refreshTokenService.issue(user, null);
        String second = refreshTokenService.issue(user, null);

        refreshTokenService.revokeAllOthers(user.getId(), "not-a-real-token");
        refreshTokenService.revokeAllOthers(user.getId(), " ");
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> refreshTokenService.consume(first)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refreshTokenService.consume(second)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokesEverythingWhenTheKeptTokenWasAlreadyRevoked() {
        User user = user("revoked@x.vn");
        String revoked = refreshTokenService.issue(user, null);
        String live = refreshTokenService.issue(user, null);
        refreshTokenService.revoke(revoked);
        entityManager.flush();
        entityManager.clear();

        refreshTokenService.revokeAllOthers(user.getId(), revoked);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> refreshTokenService.consume(live)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void leavesOtherUsersTokensUntouched() {
        User user = user("mine@x.vn");
        User neighbour = user("neighbour@x.vn");
        String mine = refreshTokenService.issue(user, null);
        String neighbours = refreshTokenService.issue(neighbour, null);

        refreshTokenService.revokeAllOthers(user.getId(), mine);
        entityManager.flush();
        entityManager.clear();

        assertThat(refreshTokenService.consume(neighbours).userId()).isEqualTo(neighbour.getId());
    }

    private User user(String username) {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username);
        user.setCredentialHash("hash");
        return userRepository.saveAndFlush(user);
    }
}
