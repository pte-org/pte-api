package com.pte.chat.internal.service.cache;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.internal.repository.ChatAnswerCacheRepository;
import com.pte.chat.internal.service.ChatErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
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

    public ChatAnswerCacheService(ChatAnswerCacheRepository repository, NamedParameterJdbcTemplate jdbc,
                                  Clock clock) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<String> find(String cacheKey) {
        return jdbc.query("SELECT answer FROM chat_answer_cache WHERE cache_key = :k AND deleted = false "
                        + "AND (expires_at IS NULL OR expires_at > :now)",
                new MapSqlParameterSource("k", cacheKey).addValue("now", Timestamp.from(clock.instant())),
                (rs, n) -> rs.getString(1)).stream().findFirst();
    }

    public void storeQuietly(String cacheKey, String answer, String model) {
        try {
            jdbc.update("INSERT INTO chat_answer_cache (public_id, created_at, updated_at, cache_key, answer, "
                            + "prompt_version, model) VALUES (:pid, :now, :now, :k, :a, :v, :m) "
                            + "ON CONFLICT (cache_key) DO NOTHING",
                    new MapSqlParameterSource("pid", UUID.randomUUID())
                            .addValue("now", Timestamp.from(clock.instant())).addValue("k", cacheKey)
                            .addValue("a", answer).addValue("v", ChatPrompts.VERSION).addValue("m", model));
        } catch (Exception ex) {
            log.warn("Chat answer cache write failed: {}", ChatErrors.describe(ex));
        }
    }
}
