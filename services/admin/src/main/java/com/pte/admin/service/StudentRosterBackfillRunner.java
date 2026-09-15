package com.pte.admin.service;

import com.pte.admin.repository.ProjectionBackfillRepository;
import com.pte.admin.service.StudentRosterRebuildService.RebuildSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Closes the cold-start gap between Flyway and an event-sourced projection.
 *
 * <p>{@code V2__create_student_roster_entries.sql} creates the roster table
 * empty, and nothing ever fills it in for students that already existed:
 * {@code outbox.iam.exchange} is a topic exchange, so every {@code UserCreated}
 * event IAM published before this service first declared {@code
 * admin.user-events} was discarded with no queue bound to receive it. The live
 * consumer only covers students created from the deploy onward, which is why
 * the gap is invisible on a freshly seeded local stack and total on an
 * environment that had real users before the projection shipped.
 *
 * <p>Runs exactly once per environment, tracked in {@code projection_backfills}
 * rather than inferred from "is the roster empty?" — a single student created
 * after the deploy makes the roster non-empty while every older student is
 * still missing, so an emptiness check would read as healthy precisely when the
 * backfill is most needed.
 *
 * <p>Deliberately never fails startup. The roster is one page of the tenant
 * portal; IAM being slow to answer must not keep this service out of the load
 * balancer. A run that exhausts its retries releases its claim and logs an
 * error, leaving {@code POST /internal/rebuild/students} as the operator path.
 */
@Component
public class StudentRosterBackfillRunner {

    /**
     * Versioned: bump the suffix to force a re-run if the projection's shape
     * ever changes in a way that needs re-deriving from IAM.
     */
    static final String BACKFILL_NAME = "student-roster-v1";

    private static final Logger log = LoggerFactory.getLogger(StudentRosterBackfillRunner.class);

    private final StudentRosterRebuildService rebuildService;
    private final ProjectionBackfillRepository backfillRepository;
    private final boolean enabled;
    private final int maxAttempts;
    private final Duration retryDelay;

    public StudentRosterBackfillRunner(StudentRosterRebuildService rebuildService,
            ProjectionBackfillRepository backfillRepository,
            @Value("${pte.roster.backfill.enabled:true}") boolean enabled,
            @Value("${pte.roster.backfill.max-attempts:10}") int maxAttempts,
            @Value("${pte.roster.backfill.retry-delay:15s}") Duration retryDelay) {
        this.rebuildService = rebuildService;
        this.backfillRepository = backfillRepository;
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            log.info("Student roster backfill disabled; skipping");
            return;
        }
        // Off the startup thread: ApplicationReadyEvent listeners run before the
        // process settles into serving, and this one waits on another service.
        Thread worker = new Thread(this::run, "student-roster-backfill");
        worker.setDaemon(true);
        worker.start();
    }

    /** Package-private so tests can drive a full run synchronously. */
    void run() {
        if (backfillRepository.claim(BACKFILL_NAME, Instant.now()) == 0) {
            log.debug("Student roster backfill '{}' already claimed; skipping", BACKFILL_NAME);
            return;
        }

        log.info("Claimed student roster backfill '{}'; rebuilding from IAM", BACKFILL_NAME);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                RebuildSummary summary = rebuildService.rebuildAll();
                backfillRepository.markCompleted(BACKFILL_NAME, Instant.now(), summary.rowsApplied());
                log.info("Student roster backfill '{}' complete: {} rows applied", BACKFILL_NAME,
                        summary.rowsApplied());
                return;
            } catch (RuntimeException e) {
                // Expected on a cold `compose up`: admin depends_on pg-core and
                // rabbitmq but not iam, so IAM's export endpoint is routinely
                // unreachable for the first attempts.
                log.warn("Student roster backfill attempt {}/{} failed: {}", attempt, maxAttempts, e.toString());
                if (attempt < maxAttempts && !sleepBeforeRetry()) {
                    break;
                }
            }
        }

        backfillRepository.releaseClaim(BACKFILL_NAME);
        log.error("Student roster backfill '{}' gave up after {} attempts. Students created before the "
                + "projection shipped stay hidden until POST /internal/rebuild/students is run.",
                BACKFILL_NAME, maxAttempts);
    }

    /** Returns false when the wait was interrupted and the run should stop. */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(retryDelay.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
