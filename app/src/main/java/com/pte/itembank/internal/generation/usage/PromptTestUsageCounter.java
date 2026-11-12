package com.pte.itembank.internal.generation.usage;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Counts prompt test runs in Redis: one key per user and day, no table. The count and its expiry are set by one
 * script, so runs that arrive together are each given their own number and a key can never be left without an
 * expiry. The key holds only a number and disappears by itself two days later.
 */
@Component
public class PromptTestUsageCounter {

    static final String KEY_PREFIX = "prompt-test:runs:";
    static final String KEY_SEPARATOR = ":";
    /** Longer than a day, so a key is still there for every run of its own day. */
    static final Duration RETENTION = Duration.ofDays(2);
    private static final String NO_REPLY = "Redis returned no count for a prompt test run";
    private static final RedisScript<Long> COUNT_RUN = RedisScript.of(
            "local runs = redis.call('INCR', KEYS[1]) "
                    + "if runs == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return runs", Long.class);

    private final StringRedisTemplate redis;

    public PromptTestUsageCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Counts one more run of that user on that day and returns how many there are now, this one included. */
    public long countRun(UUID userPublicId, LocalDate day) {
        Long runs = redis.execute(COUNT_RUN, List.of(KEY_PREFIX + userPublicId + KEY_SEPARATOR + day),
                String.valueOf(RETENTION.toSeconds()));
        if (runs == null) {
            throw new IllegalStateException(NO_REPLY);
        }
        return runs;
    }
}
