package com.pte.attempt.internal.service.cache;

import com.pte.attempt.internal.constant.AttemptConstants;
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
 * Warm cache with single-flight for the runtime view of one attempt's frozen exam. The exam is immutable once
 * published, so a long fixed TTL is a memory-bounded stand-in for "forever": it only has to comfortably exceed
 * the longest real exam. The key is the attempt, not the session: the presigned media URLs inside the view are
 * sized to that attempt's own deadline, so sharing them across attempts could hand a later starter expired links.
 */
@Component
public class ExamContentCache {

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final int MAX_WAIT_RETRIES = 20;
    private static final long WAIT_RETRY_MS = 100;

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public ExamContentCache(StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    /** Called right after an attempt loads the exam, so the very first read is already a cache hit. */
    public void warm(UUID attemptPublicId, List<ExamItemView> items) {
        write(attemptPublicId, items);
    }

    /**
     * Cache-first read with single-flight on miss: only the caller holding the {@code SET NX} lock builds the
     * view; concurrent callers poll the cache instead of stampeding the loader.
     */
    public List<ExamItemView> getOrLoad(UUID attemptPublicId, Supplier<List<ExamItemView>> loader) {
        Optional<List<ExamItemView>> cached = read(attemptPublicId);
        if (cached.isPresent()) {
            return cached.get();
        }
        String lockKey = AttemptConstants.LOCK_KEY_PREFIX + attemptPublicId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            try {
                List<ExamItemView> loaded = loader.get();
                write(attemptPublicId, loaded);
                return loaded;
            } finally {
                redisTemplate.delete(lockKey);
            }
        }
        return waitForCache(attemptPublicId, loader);
    }

    private List<ExamItemView> waitForCache(UUID attemptPublicId, Supplier<List<ExamItemView>> loader) {
        for (int attempt = 0; attempt < MAX_WAIT_RETRIES; attempt++) {
            Optional<List<ExamItemView>> cached = read(attemptPublicId);
            if (cached.isPresent()) {
                return cached.get();
            }
            sleep();
        }
        // The lock holder took too long (or crashed): build directly rather than waiting forever.
        return loader.get();
    }

    private void sleep() {
        try {
            Thread.sleep(WAIT_RETRY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Optional<List<ExamItemView>> read(UUID attemptPublicId) {
        String json = redisTemplate.opsForValue().get(AttemptConstants.CACHE_KEY_PREFIX + attemptPublicId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(json, new TypeReference<List<ExamItemView>>() {
            }));
        } catch (JacksonException ex) {
            return Optional.empty();
        }
    }

    private void write(UUID attemptPublicId, List<ExamItemView> items) {
        try {
            String json = jsonMapper.writeValueAsString(items);
            redisTemplate.opsForValue().set(AttemptConstants.CACHE_KEY_PREFIX + attemptPublicId, json, CACHE_TTL);
        } catch (JacksonException ex) {
            throw new IllegalStateException(AttemptConstants.EXAM_CONTENT_CACHE_SERIALIZATION_FAILED, ex);
        }
    }
}
