package com.pte.identity.internal.service;

import com.pte.identity.domain.User;
import com.pte.identity.internal.exception.InvalidRefreshTokenException;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.TokenHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 2: a refresh token is bound to the organization chosen at login and never crosses organizations. */
@DataJpaTest
@Import({RefreshTokenService.class, TokenHasher.class})
class RefreshTokenOrganizationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void consumingATokenReturnsTheOrganizationItWasIssuedFor() {
        User user = user("multi@x.vn");
        UUID orgB = UUID.randomUUID();

        String raw = refreshTokenService.issue(user, orgB);
        RefreshSession session = refreshTokenService.consume(raw);

        assertThat(session.userId()).isEqualTo(user.getId());
        assertThat(session.organizationId()).isEqualTo(orgB);
    }

    @Test
    void platformUsersGetATokenWithoutAnOrganization() {
        User admin = user("admin@x.vn");

        RefreshSession session = refreshTokenService.consume(refreshTokenService.issue(admin, null));

        assertThat(session.organizationId()).isNull();
    }

    @Test
    void aConsumedTokenCannotBeReplayed() {
        User user = user("replay@x.vn");
        String raw = refreshTokenService.issue(user, UUID.randomUUID());
        refreshTokenService.consume(raw);

        assertThatThrownBy(() -> refreshTokenService.consume(raw)).isInstanceOf(InvalidRefreshTokenException.class);
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
