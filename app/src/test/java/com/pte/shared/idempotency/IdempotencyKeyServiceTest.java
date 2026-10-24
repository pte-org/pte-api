package com.pte.shared.idempotency;

import com.pte.shared.idempotency.domain.IdempotencyKey;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import com.pte.shared.idempotency.internal.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 1: one generic dedupe + receipt table replaces every *Command/*Operation/*Intent entity. */
@DataJpaTest
@Import(IdempotencyKeyService.class)
class IdempotencyKeyServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");

    @Autowired
    private IdempotencyKeyService service;

    @Autowired
    private IdempotencyKeyRepository repository;

    @Test
    void firstUseOfAKeyIsNewAndLaterReplayReturnsTheStoredReceipt() {
        UUID organizationId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        var first = service.begin(IdempotencyScope.ATTENDANCE, key, organizationId, "hash-1", NOW);
        assertThat(first.outcome()).isEqualTo(IdempotencyOutcome.NEW);

        service.complete(IdempotencyScope.ATTENDANCE, key, organizationId, "{\"changed\":true}");

        var replay = service.begin(IdempotencyScope.ATTENDANCE, key, organizationId, "hash-1", NOW);
        assertThat(replay.outcome()).isEqualTo(IdempotencyOutcome.REPLAY);
        assertThat(replay.resultJson()).contains("\"changed\":true");
    }

    @Test
    void sameKeyWithDifferentRequestHashIsAConflict() {
        UUID organizationId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        service.begin(IdempotencyScope.LICENSE_ISSUE, key, organizationId, "hash-a", NOW);

        var second = service.begin(IdempotencyScope.LICENSE_ISSUE, key, organizationId, "hash-b", NOW);

        assertThat(second.outcome()).isEqualTo(IdempotencyOutcome.CONFLICT);
    }

    @Test
    void sameKeyIsIndependentAcrossScopesAndOrganizations() {
        String key = UUID.randomUUID().toString();
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        service.begin(IdempotencyScope.ATTENDANCE, key, orgA, "h", NOW);

        assertThat(service.begin(IdempotencyScope.EXAMINER_COMMAND, key, orgA, "h", NOW).outcome())
                .isEqualTo(IdempotencyOutcome.NEW);
        assertThat(service.begin(IdempotencyScope.ATTENDANCE, key, orgB, "h", NOW).outcome())
                .isEqualTo(IdempotencyOutcome.NEW);
    }

    @Test
    void retentionIsDeclaredPerScope() {
        Instant anchor = Instant.parse("2026-11-01T00:00:00Z");

        assertThat(IdempotencyScope.ATTENDANCE.expiresAt(anchor)).isEqualTo(anchor.plus(Duration.ofDays(90)));
        assertThat(IdempotencyScope.EXAMINER_COMMAND.expiresAt(anchor)).isEqualTo(anchor.plus(Duration.ofDays(90)));
        assertThat(IdempotencyScope.LICENSE_ISSUE.expiresAt(anchor)).isEqualTo(anchor.plus(Duration.ofDays(365)));
        assertThat(IdempotencyScope.PRACTICE_SESSION_OP.expiresAt(anchor)).isEqualTo(anchor.plus(Duration.ofDays(7)));
    }

    @Test
    void purgeExpiredRemovesOnlyRowsPastTheirExpiry() {
        UUID organizationId = UUID.randomUUID();
        service.begin(IdempotencyScope.PRACTICE_SESSION_OP, "old", organizationId, "h", NOW.minus(Duration.ofDays(30)));
        service.begin(IdempotencyScope.LICENSE_ISSUE, "fresh", organizationId, "h", NOW);

        long purged = service.purgeExpired(NOW);

        assertThat(purged).isEqualTo(1);
        assertThat(repository.findAll()).extracting(IdempotencyKey::getKey).containsExactly("fresh");
    }

    @Test
    void completingAnUnknownKeyIsRejected() {
        assertThatThrownBy(() -> service.complete(IdempotencyScope.ATTENDANCE, "missing", UUID.randomUUID(), "{}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void platformLevelCommandsWithoutAnOrganizationAreDeduplicatedToo() {
        String key = UUID.randomUUID().toString();

        var first = service.begin(IdempotencyScope.LICENSE_ISSUE, key, null, "hash", NOW);
        service.complete(IdempotencyScope.LICENSE_ISSUE, key, null, "{\"issued\":true}");
        var replay = service.begin(IdempotencyScope.LICENSE_ISSUE, key, null, "hash", NOW);

        assertThat(first.outcome()).isEqualTo(IdempotencyOutcome.NEW);
        assertThat(replay.outcome()).isEqualTo(IdempotencyOutcome.REPLAY);
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void anIncompleteFirstAttemptIsAConflictForARetryWithTheSameHash() {
        UUID organizationId = UUID.randomUUID();
        service.begin(IdempotencyScope.ATTENDANCE, "k", organizationId, "same", NOW);

        assertThat(service.begin(IdempotencyScope.ATTENDANCE, "k", organizationId, "same", NOW).outcome())
                .isEqualTo(IdempotencyOutcome.CONFLICT);
    }
}
