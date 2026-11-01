package com.pte.identity.internal.repository;

import com.pte.identity.internal.domain.PasswordResetToken;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** A link can be spent exactly once, even when two requests present it together. */
@DataJpaTest
class PasswordResetTokenRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void markUsed_succeedsOnceAndThenReportsZeroRows() {
        PasswordResetToken token = save(1L, "hash-a");

        assertThat(tokenRepository.markUsed(token.getId(), NOW)).isEqualTo(1);
        assertThat(tokenRepository.markUsed(token.getId(), NOW.plusSeconds(5))).isZero();

        entityManager.flush();
        entityManager.clear();
        assertThat(tokenRepository.findByTokenHash("hash-a")).get()
                .extracting(PasswordResetToken::getUsedAt).isEqualTo(NOW);
    }

    @Test
    void invalidateActiveByUserId_spendsOnlyThatUsersUnusedTokens() {
        PasswordResetToken mine = save(1L, "hash-mine");
        PasswordResetToken alreadyUsed = save(1L, "hash-used");
        tokenRepository.markUsed(alreadyUsed.getId(), NOW.minusSeconds(60));
        PasswordResetToken neighbours = save(2L, "hash-neighbour");

        int spent = tokenRepository.invalidateActiveByUserId(1L, NOW);
        entityManager.flush();
        entityManager.clear();

        assertThat(spent).isEqualTo(1);
        assertThat(tokenRepository.markUsed(mine.getId(), NOW)).isZero();
        assertThat(tokenRepository.markUsed(neighbours.getId(), NOW)).isEqualTo(1);
    }

    @Test
    void findByTokenHash_findsTheStoredRowAndNothingForAnUnknownHash() {
        save(1L, "hash-known");

        assertThat(tokenRepository.findByTokenHash("hash-known")).isPresent();
        assertThat(tokenRepository.findByTokenHash("hash-unknown")).isEmpty();
    }

    private PasswordResetToken save(Long userId, String hash) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setTokenHash(hash);
        token.setExpiresAt(NOW.plusSeconds(1800));
        return tokenRepository.saveAndFlush(token);
    }
}
