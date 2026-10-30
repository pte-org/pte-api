package com.pte.chat.internal.repository;

import com.pte.chat.internal.domain.ChatAnswerCache;
import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.domain.ChatSession;
import com.pte.chat.support.ChatPostgres;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hibernate {@code ddl-auto: validate} against the Flyway-built Postgres schema:
 * if the chat entities drift from V7, the context fails to start. The persist
 * round trips prove the mapping also works at the SQL level, not just in metadata.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-test")
@ContextConfiguration(classes = ChatSchemaValidatePostgresTest.ChatJpaConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class ChatSchemaValidatePostgresTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = ChatMessage.class)
    @EnableJpaRepositories(basePackageClasses = ChatMessageRepository.class)
    static class ChatJpaConfiguration {
    }

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(ChatPostgres.IMAGE);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private ChatSessionRepository sessions;
    @Autowired
    private ChatMessageRepository messages;
    @Autowired
    private ChatAnswerCacheRepository caches;
    @Autowired
    private EntityManager entityManager;

    @Test
    void hibernate_validate_passes_and_session_round_trips_on_postgres() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setStudentId(UUID.randomUUID());
        session.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        sessions.saveAndFlush(session);
        entityManager.clear();

        assertThat(sessions.findById(sessionId)).isPresent();
    }

    @Test
    void message_with_null_on_topic_and_empty_answer_round_trips_on_postgres() {
        ChatMessage message = new ChatMessage();
        message.setSessionId(UUID.randomUUID());
        message.setStudentId(UUID.randomUUID());
        message.setTenantId(UUID.randomUUID());
        message.setQuestion("How do I improve my score?");
        message.setAnswer("");
        message.setStatus("FAILED");
        message.setOnTopic(null);
        message.setTokensIn(10);
        message.setTokensOut(0);
        message.setFinishReason("MAX_TOKENS");

        Long id = messages.saveAndFlush(message).getId();
        entityManager.clear();

        ChatMessage reloaded = messages.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("FAILED");
        assertThat(reloaded.getOnTopic()).isNull();
        assertThat(reloaded.getPublicId()).isNotNull();
    }

    @Test
    void answer_cache_round_trips_and_is_found_by_key_on_postgres() {
        String key = "f".repeat(64);
        ChatAnswerCache cache = new ChatAnswerCache();
        cache.setCacheKey(key);
        cache.setAnswer("cached");
        cache.setPromptVersion("v1");
        cache.setModel("gemini-2.5-flash-lite");
        caches.saveAndFlush(cache);
        entityManager.clear();

        assertThat(caches.findByCacheKey(key)).isPresent();
    }
}
