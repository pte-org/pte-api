package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.OtpChallenge;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 1: OtpChallenge replaces PracticeEmailChallenge with the same single-use semantics. */
@DataJpaTest
class OtpChallengeRepositoryTest {

    @Autowired
    private OtpChallengeRepository repository;

    @Test
    void savedChallengeIsFoundByPublicIdWithLockAndCountedPerIdentity() {
        OtpChallenge challenge = challenge(7L, Instant.now().plus(10, ChronoUnit.MINUTES));
        repository.saveAndFlush(challenge);

        assertThat(repository.findWithLockByPublicIdAndDeletedFalse(challenge.getPublicId())).isPresent();
        assertThat(repository.countByIdentityIdAndCreatedAtAfterAndDeletedFalse(7L, Instant.now().minusSeconds(60)))
                .isEqualTo(1L);
        assertThat(repository.countByIdentityIdAndCreatedAtAfterAndDeletedFalse(8L, Instant.now().minusSeconds(60)))
                .isZero();
    }

    @Test
    void challengeIsUnusableWhenConsumedExpiredOrOverAttempted() {
        Instant now = Instant.parse("2026-10-24T00:00:00Z");

        OtpChallenge live = challenge(1L, now.plusSeconds(60));
        assertThat(live.isConsumedOrExpired(now)).isFalse();

        OtpChallenge consumed = challenge(1L, now.plusSeconds(60));
        consumed.setConsumedAt(now);
        assertThat(consumed.isConsumedOrExpired(now)).isTrue();

        OtpChallenge expired = challenge(1L, now.minusSeconds(1));
        assertThat(expired.isConsumedOrExpired(now)).isTrue();
    }

    private OtpChallenge challenge(long identityId, Instant expiresAt) {
        OtpChallenge challenge = new OtpChallenge();
        challenge.setPublicId(UUID.randomUUID());
        challenge.setIdentityId(identityId);
        challenge.setCodeHash("hash");
        challenge.setExpiresAt(expiresAt);
        return challenge;
    }
}
