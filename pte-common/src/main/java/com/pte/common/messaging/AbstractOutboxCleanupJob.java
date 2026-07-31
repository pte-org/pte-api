package com.pte.common.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Daily batched deletion of already-published outbox rows, deliberately
 * isolated from {@link AbstractOutboxRelay}'s hot poll transaction so
 * cleanup never blocks, or is blocked by, publishing.
 *
 * @param <T> the service's concrete outbox entity
 */
@Slf4j
public abstract class AbstractOutboxCleanupJob<T extends AbstractOutboxEntry> {

    private final OutboxJpaRepository<T> repository;

    @Value("${pte.outbox.cleanup-retention-days:7}")
    private int retentionDays;

    protected AbstractOutboxCleanupJob(OutboxJpaRepository<T> repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${pte.outbox.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanup() {
        Instant cutoff = cutoff();
        int deleted = repository.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            log.info("Outbox cleanup: removed {} published row(s) older than {}", deleted, cutoff);
        }
    }

    /** Package-private so a unit test can verify the boundary without a real clock or DB. */
    Instant cutoff() {
        return Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    }
}
