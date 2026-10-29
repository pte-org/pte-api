package com.pte.chat.internal.service.cache;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.internal.repository.ChatAnswerCacheRepository;
import com.pte.chat.internal.service.ChatErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared answers for the first question of a conversation. Writing is best effort: a cache failure must
 * never break a reply that was already paid for, so every exception is swallowed after a log line. The key
 * includes the prompt version, so changing the prompt or the model (bump the version) retires old entries.
 */
@Service
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatAnswerCacheService {

    private static final Logger log = LoggerFactory.getLogger(ChatAnswerCacheService.class);

    private final ChatAnswerCacheRepository repository;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ChatAiProperties properties;

    @Autowired
    public ChatAnswerCacheService(ChatAnswerCacheRepository repository, NamedParameterJdbcTemplate jdbc,
                                  Clock clock, ChatAiProperties properties) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
    }

    /** With the default lifetime; kept for callers that do not tune {@code chat.ai.retention-cache-days}. */
    public ChatAnswerCacheService(ChatAnswerCacheRepository repository, NamedParameterJdbcTemplate jdbc,
                                  Clock clock) {
        this(repository, jdbc, clock, new ChatAiProperties());
    }

    public Optional<String> find(String cacheKey) {
        return jdbc.query("SELECT answer FROM chat_answer_cache WHERE cache_key = :k AND deleted = false "
                        + "AND (expires_at IS NULL OR expires_at > :now)",
                new MapSqlParameterSource("k", cacheKey).addValue("now", Timestamp.from(clock.instant())),
                (rs, n) -> rs.getString(1)).stream().findFirst();
    }

    public void storeQuietly(String cacheKey, String answer, String model) {
        try {
            Instant now = clock.instant();
            // A live entry wins (first write); an expired one is replaced, since its unique key would otherwise
            // block every later answer for the same question until the nightly clean-up deletes it.
            jdbc.update("INSERT INTO chat_answer_cache (public_id, created_at, updated_at, cache_key, answer, "
                            + "prompt_version, model, expires_at) VALUES (:pid, :now, :now, :k, :a, :v, :m, :exp) "
                            + "ON CONFLICT (cache_key) DO UPDATE SET answer = EXCLUDED.answer, "
                            + "prompt_version = EXCLUDED.prompt_version, model = EXCLUDED.model, "
                            + "updated_at = EXCLUDED.updated_at, expires_at = EXCLUDED.expires_at "
                            + "WHERE chat_answer_cache.expires_at IS NOT NULL AND chat_answer_cache.expires_at <= :now",
                    new MapSqlParameterSource("pid", UUID.randomUUID())
                            .addValue("now", Timestamp.from(now)).addValue("k", cacheKey)
                            .addValue("a", answer).addValue("v", ChatPrompts.VERSION).addValue("m", model)
                            .addValue("exp", Timestamp.from(now.plus(properties.getRetentionCacheDays(), ChronoUnit.DAYS))));
        } catch (Exception ex) {
            log.warn("Chat answer cache write failed: {}", ChatErrors.describe(ex));
        }
    }
}
