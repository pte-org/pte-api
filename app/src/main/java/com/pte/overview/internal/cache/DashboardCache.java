package com.pte.overview.internal.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Short-TTL read-through cache over the existing Redis template. Any cache
 * failure (connection, timeout, bad payload) degrades to computing directly:
 * the cache can make a request faster, never make it fail. Exceptions from the
 * loader itself are not swallowed.
 */
@Component
public class DashboardCache {

    private static final Logger log = LoggerFactory.getLogger(DashboardCache.class);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public DashboardCache(StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    public <T> T getOrCompute(String key, Duration ttl, Class<T> type, Supplier<T> loader) {
        T cached = readQuietly(key, type);
        if (cached != null) {
            return cached;
        }
        T value = loader.get();
        writeQuietly(key, ttl, value);
        return value;
    }

    private <T> T readQuietly(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : jsonMapper.readValue(json, type);
        } catch (RuntimeException ex) {
            log.warn("Dashboard cache read failed for key {}; computing directly: {}", key, ex.toString());
            return null;
        }
    }

    private void writeQuietly(String key, Duration ttl, Object value) {
        try {
            redisTemplate.opsForValue().set(key, jsonMapper.writeValueAsString(value), ttl);
        } catch (RuntimeException ex) {
            log.warn("Dashboard cache write failed for key {}: {}", key, ex.toString());
        }
    }
}
