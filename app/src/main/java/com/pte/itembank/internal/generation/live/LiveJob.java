package com.pte.itembank.internal.generation.live;

import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;
import com.pte.itembank.internal.generation.llm.LlmRole;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The live state of one job and the streams that watch it. The worker writes and a new watcher reads under the same
 * lock, so a watcher gets the snapshot and then every later event, none twice and none lost.
 */
final class LiveJob {

    private final TreeMap<Integer, LiveItem> items = new TreeMap<>();
    private final List<SseEmitter> watchers = new ArrayList<>();
    private Instant lastActivity;
    private Instant finishedAt;

    LiveJob(Instant now) {
        lastActivity = now;
    }

    synchronized void begin(int index, LlmRole agent, Instant now) {
        int attempt = item(index).begin(agent);
        touch(now);
        send(GenerationLiveConstants.EVENT_BEGIN, new LiveEvent.Begin(index, agent.name(), attempt));
    }

    synchronized void token(int index, LlmRole agent, String delta, Instant now) {
        item(index).append(agent, delta);
        touch(now);
        send(GenerationLiveConstants.EVENT_TOKEN, new LiveEvent.Token(index, agent.name(), delta));
    }

    synchronized void stage(int index, String stage, Instant now) {
        item(index).stage(stage);
        touch(now);
        send(GenerationLiveConstants.EVENT_STAGE, new LiveEvent.Stage(index, stage));
    }

    synchronized void media(int index, UUID audioRef, UUID imageRef, Instant now) {
        item(index).media(audioRef, imageRef);
        touch(now);
        send(GenerationLiveConstants.EVENT_MEDIA, new LiveEvent.Media(index, audioRef, imageRef));
    }

    synchronized void draft(LiveEvent.Draft draft, Instant now) {
        item(draft.index()).draft(draft);
        touch(now);
        send(GenerationLiveConstants.EVENT_DRAFT, draft);
    }

    synchronized void review(LiveEvent.Review review, Instant now) {
        item(review.index()).review(review);
        touch(now);
        send(GenerationLiveConstants.EVENT_REVIEW, review);
    }

    synchronized void itemEnded(int index, Instant now) {
        touch(now);
        send(GenerationLiveConstants.EVENT_ITEM, new LiveEvent.Item(index));
    }

    /** Tells every watcher the job ended and closes their streams; the state stays for a late watcher. */
    synchronized void finish(Instant now) {
        touch(now);
        finishedAt = now;
        send(GenerationLiveConstants.EVENT_END, new LiveEvent.End(true));
        watchers.forEach(SseEmitter::complete);
        watchers.clear();
    }

    synchronized void attach(SseEmitter watcher) {
        List<LiveEvent.ItemSnapshot> snapshot = items.values().stream().map(LiveItem::snapshot).toList();
        if (!deliver(watcher, GenerationLiveConstants.EVENT_SNAPSHOT,
                new LiveEvent.Snapshot(snapshot, finishedAt != null))) {
            return;
        }
        if (finishedAt != null) {
            deliver(watcher, GenerationLiveConstants.EVENT_END, new LiveEvent.End(true));
            watcher.complete();
            return;
        }
        watchers.add(watcher);
    }

    synchronized void detach(SseEmitter watcher) {
        watchers.remove(watcher);
    }

    synchronized void heartbeat() {
        Iterator<SseEmitter> each = watchers.iterator();
        while (each.hasNext()) {
            try {
                each.next().send(SseEmitter.event().comment(GenerationLiveConstants.HEARTBEAT_COMMENT));
            } catch (IOException | IllegalStateException gone) {
                each.remove();
            }
        }
    }

    /** A finished job is kept a while for late watchers; an unwatched job nothing writes to is dropped too. */
    synchronized boolean isExpired(Instant now) {
        return isExpired(now, GenerationLiveConstants.FINISHED_RETENTION_MS, GenerationLiveConstants.IDLE_RETENTION_MS);
    }

    synchronized boolean isExpired(Instant now, long finishedRetentionMs, long idleRetentionMs) {
        if (finishedAt != null) {
            return Duration.between(finishedAt, now).toMillis() > finishedRetentionMs;
        }
        return watchers.isEmpty() && Duration.between(lastActivity, now).toMillis() > idleRetentionMs;
    }

    private LiveItem item(int index) {
        return items.computeIfAbsent(index, LiveItem::new);
    }

    /** Any write means the job runs (again, when a resumed job was ended by an aborted run). */
    private void touch(Instant now) {
        lastActivity = now;
        finishedAt = null;
    }

    private void send(String name, Object payload) {
        watchers.removeIf(watcher -> !deliver(watcher, name, payload));
    }

    /** @return false when the watcher went away; the stream is then dropped, never retried */
    private static boolean deliver(SseEmitter watcher, String name, Object payload) {
        try {
            watcher.send(SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException gone) {
            return false;
        }
    }
}
