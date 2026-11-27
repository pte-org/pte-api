package com.pte.itembank.internal.generation.live;

import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The live state of the jobs of this instance, in memory only. The worker runs in the same process as the API, so
 * the job a worker writes is the job a watcher reads; nothing here is persisted or shared between instances.
 */
@Component
class LiveJobRegistry {

    private final Map<UUID, LiveJob> jobs = new ConcurrentHashMap<>();
    private final long finishedRetentionMs;
    private final long idleRetentionMs;
    private final Clock clock;

    LiveJobRegistry(Clock clock) {
        this(GenerationLiveConstants.FINISHED_RETENTION_MS, GenerationLiveConstants.IDLE_RETENTION_MS, clock);
    }

    /** How long live drafts stay in memory: {@code itembank.generation.live.*}; the constants are the defaults. */
    @Autowired
    LiveJobRegistry(
            @Value("${itembank.generation.live.finished-retention-ms:" + GenerationLiveConstants.FINISHED_RETENTION_MS
                    + "}") long finishedRetentionMs,
            @Value("${itembank.generation.live.idle-retention-ms:" + GenerationLiveConstants.IDLE_RETENTION_MS + "}")
            long idleRetentionMs,
            Clock clock) {
        this.finishedRetentionMs = finishedRetentionMs;
        this.idleRetentionMs = idleRetentionMs;
        this.clock = clock;
    }

    LiveJob open(UUID jobPublicId) {
        return jobs.computeIfAbsent(jobPublicId, ignored -> new LiveJob(clock.instant()));
    }

    /**
     * A worker starts (or starts again) the job: a state left from an earlier run that ended is replaced, so a
     * job resumed after a pause is followed afresh instead of being reported as ended. A live state is kept.
     */
    LiveJob start(UUID jobPublicId) {
        return jobs.compute(jobPublicId,
                (ignored, current) -> current == null || current.isFinished() ? new LiveJob(clock.instant()) : current);
    }

    void forEach(Consumer<LiveJob> action) {
        jobs.values().forEach(action);
    }

    void dropExpired() {
        var now = clock.instant();
        jobs.values().removeIf(job -> job.isExpired(now, finishedRetentionMs, idleRetentionMs));
    }

    Clock clock() {
        return clock;
    }
}
