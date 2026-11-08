package com.pte.itembank.internal.generation.live;

import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Opens the live stream of a job for one watcher and keeps the open streams alive. The caller checks access to the
 * job first; this class only knows job ids.
 */
@Component
public class GenerationLiveSubscriptions {

    private final LiveJobRegistry registry;
    private final long emitterTimeoutMs;

    /** A stream's longest life comes from {@code itembank.generation.live.emitter-timeout-ms}. */
    public GenerationLiveSubscriptions(LiveJobRegistry registry,
            @Value("${itembank.generation.live.emitter-timeout-ms:" + GenerationLiveConstants.EMITTER_TIMEOUT_MS + "}")
            long emitterTimeoutMs) {
        this.registry = registry;
        this.emitterTimeoutMs = emitterTimeoutMs;
    }

    /** The stream starts with a snapshot of what is known, then carries every later event of the job. */
    public SseEmitter subscribe(UUID jobPublicId) {
        SseEmitter watcher = new SseEmitter(emitterTimeoutMs);
        LiveJob job = registry.open(jobPublicId);
        watcher.onCompletion(() -> job.detach(watcher));
        watcher.onTimeout(() -> job.detach(watcher));
        watcher.onError(ignored -> job.detach(watcher));
        job.attach(watcher);
        return watcher;
    }

    @Scheduled(fixedRateString = "${itembank.generation.live.heartbeat-interval-ms:"
            + GenerationLiveConstants.HEARTBEAT_INTERVAL_MS + "}")
    public void keepAlive() {
        registry.forEach(LiveJob::heartbeat);
        registry.dropExpired();
    }
}
