package com.pte.examdelivery.service.cache;

import com.pte.examdelivery.constant.ExamDeliveryConstants;
import com.pte.examdelivery.domain.PinnedItem;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Warm cache + single-flight for pinned-snapshot reads (ADR-003). The pinned
 * snapshot is immutable once created, so a long fixed TTL is used as a
 * pragmatic, memory-bounded stand-in for "infinite" (real infinite retention
 * would grow Redis unboundedly across attempts — TTL just needs to comfortably
 * exceed the longest real exam session, not last forever).
 */
@Component
public class PinnedSnapshotCacheService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final int MAX_WAIT_RETRIES = 20;
    private static final long WAIT_RETRY_MS = 100;

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public PinnedSnapshotCacheService(StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    /** Called right after pinning, so the very first read is already a cache hit. */
    public void warm(UUID pinnedSnapshotPublicId, List<PinnedItemView> items) {
        write(pinnedSnapshotPublicId, items);
    }

    /**
     * Cache-first read with single-flight on miss: only the goroutine holding
     * the {@code SET NX} lock queries Postgres; concurrent callers poll the
     * cache instead of stampeding the DB (ADR-003 single-flight pattern).
     */
    public List<PinnedItemView> getOrLoad(UUID pinnedSnapshotPublicId, Supplier<List<PinnedItem>> dbLoader) {
        Optional<List<PinnedItemView>> cached = read(pinnedSnapshotPublicId);
        if (cached.isPresent()) {
            return cached.get();
        }

        String lockKey = ExamDeliveryConstants.LOCK_KEY_PREFIX + pinnedSnapshotPublicId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            try {
                List<PinnedItemView> loaded = dbLoader.get().stream().map(PinnedSnapshotCacheService::toView).toList();
                write(pinnedSnapshotPublicId, loaded);
                return loaded;
            } finally {
                redisTemplate.delete(lockKey);
            }
        }
        return waitForCache(pinnedSnapshotPublicId, dbLoader);
    }

    private List<PinnedItemView> waitForCache(UUID pinnedSnapshotPublicId, Supplier<List<PinnedItem>> dbLoader) {
        for (int attempt = 0; attempt < MAX_WAIT_RETRIES; attempt++) {
            Optional<List<PinnedItemView>> cached = read(pinnedSnapshotPublicId);
            if (cached.isPresent()) {
                return cached.get();
            }
            sleep();
        }
        // Lock holder took too long (or crashed) — fall back to a direct DB read
        // rather than waiting forever. Does not re-attempt to become the loader.
        return dbLoader.get().stream().map(PinnedSnapshotCacheService::toView).toList();
    }

    private void sleep() {
        try {
            Thread.sleep(WAIT_RETRY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Optional<List<PinnedItemView>> read(UUID pinnedSnapshotPublicId) {
        String json = redisTemplate.opsForValue().get(ExamDeliveryConstants.CACHE_KEY_PREFIX + pinnedSnapshotPublicId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(json, new TypeReference<List<PinnedItemView>>() {
            }));
        } catch (JacksonException ex) {
            return Optional.empty();
        }
    }

    private void write(UUID pinnedSnapshotPublicId, List<PinnedItemView> items) {
        try {
            String json = jsonMapper.writeValueAsString(items);
            redisTemplate.opsForValue().set(ExamDeliveryConstants.CACHE_KEY_PREFIX + pinnedSnapshotPublicId, json, CACHE_TTL);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize pinned snapshot cache entry", ex);
        }
    }

    public static PinnedItemView toView(PinnedItem item) {
        return new PinnedItemView(item.getPublicId(), item.getOrderIndex(), item.getSection(), item.getTaskType(),
                item.getTitle(), item.getPromptText(), item.getAudioPromptRef(), item.getImagePromptRef(),
                item.getReferenceAnswerText(), item.getCorrectAnswerText(), item.getMinWordCount(),
                item.getMaxWordCount(), item.getOptionsJson(), item.getPrepSeconds(), item.getResponseSeconds());
    }
}
