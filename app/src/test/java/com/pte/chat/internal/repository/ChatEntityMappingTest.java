package com.pte.chat.internal.repository;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.domain.ChatAnswerCache;
import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.domain.ChatSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Entity/DDL-free mapping checks on the default H2 create-drop context (no jsonb, no columnDefinition). */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:chat-entity-mapping;DB_CLOSE_DELAY=-1"
})
@ContextConfiguration(classes = ChatEntityMappingTest.ChatJpaConfiguration.class)
class ChatEntityMappingTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = ChatMessage.class)
    @EnableJpaRepositories(basePackageClasses = ChatMessageRepository.class)
    static class ChatJpaConfiguration {
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
    void chat_session_round_trips_with_uuid_primary_key() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setStudentId(studentId);
        session.setCreatedAt(createdAt);

        sessions.saveAndFlush(session);
        entityManager.clear();

        ChatSession reloaded = sessions.findById(sessionId).orElseThrow();
        assertThat(reloaded.getSessionId()).isEqualTo(sessionId);
        assertThat(reloaded.getStudentId()).isEqualTo(studentId);
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void ok_message_round_trips_every_column() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ChatMessage message = message(sessionId, studentId, tenantId, ChatConstants.STATUS_OK);
        message.setAnswer("Use topic sentences.");
        message.setTaskType("WRITE_ESSAY");
        message.setLanguage("vi");
        message.setTokensIn(120);
        message.setTokensOut(80);
        message.setTokensThinking(0);
        message.setFinishReason("STOP");
        message.setFromCache(true);
        message.setOnTopic(Boolean.TRUE);
        message.setModel("gemini-2.5-flash-lite");
        message.setPromptVersion("v1");

        Long id = messages.saveAndFlush(message).getId();
        entityManager.clear();

        ChatMessage reloaded = messages.findById(id).orElseThrow();
        assertThat(reloaded.getSessionId()).isEqualTo(sessionId);
        assertThat(reloaded.getStudentId()).isEqualTo(studentId);
        assertThat(reloaded.getTenantId()).isEqualTo(tenantId);
        assertThat(reloaded.getQuestion()).isEqualTo("How do I write an essay?");
        assertThat(reloaded.getAnswer()).isEqualTo("Use topic sentences.");
        assertThat(reloaded.getTaskType()).isEqualTo("WRITE_ESSAY");
        assertThat(reloaded.getLanguage()).isEqualTo("vi");
        assertThat(reloaded.getTokensIn()).isEqualTo(120);
        assertThat(reloaded.getTokensOut()).isEqualTo(80);
        assertThat(reloaded.getTokensThinking()).isZero();
        assertThat(reloaded.getFinishReason()).isEqualTo("STOP");
        assertThat(reloaded.isFromCache()).isTrue();
        assertThat(reloaded.getOnTopic()).isTrue();
        assertThat(reloaded.getStatus()).isEqualTo(ChatConstants.STATUS_OK);
        assertThat(reloaded.getModel()).isEqualTo("gemini-2.5-flash-lite");
        assertThat(reloaded.getPromptVersion()).isEqualTo("v1");
    }

    @Test
    void failed_message_keeps_empty_answer_null_on_topic_and_token_counts() {
        ChatMessage message = message(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ChatConstants.STATUS_FAILED);
        message.setAnswer("");
        message.setOnTopic(null);
        message.setTokensIn(50);
        message.setTokensOut(0);
        message.setTokensThinking(30);
        message.setFinishReason("MAX_TOKENS");

        Long id = messages.saveAndFlush(message).getId();
        entityManager.clear();

        ChatMessage reloaded = messages.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ChatConstants.STATUS_FAILED);
        assertThat(reloaded.getOnTopic()).isNull();
        assertThat(reloaded.getTokensIn()).isEqualTo(50);
        assertThat(reloaded.getTokensOut()).isZero();
        assertThat(reloaded.getTokensThinking()).isEqualTo(30);
        assertThat(reloaded.getFinishReason()).isEqualTo("MAX_TOKENS");
        assertThat(reloaded.isFromCache()).isFalse();
        assertThat(reloaded.getAnswer()).isEmpty();
    }

    @Test
    void refused_message_stores_on_topic_false() {
        ChatMessage message = message(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ChatConstants.STATUS_REFUSED);
        message.setAnswer("I can only help with PTE preparation.");
        message.setOnTopic(Boolean.FALSE);

        Long id = messages.saveAndFlush(message).getId();
        entityManager.clear();

        ChatMessage reloaded = messages.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ChatConstants.STATUS_REFUSED);
        assertThat(reloaded.getOnTopic()).isFalse();
    }

    @Test
    void base_entity_assigns_public_id_and_timestamps_on_message() {
        ChatMessage saved = messages.saveAndFlush(message(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), ChatConstants.STATUS_OK));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.isDeleted()).isFalse();
    }

    @Test
    void answer_cache_is_found_by_cache_key_and_round_trips() {
        String key = "a".repeat(64);
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        ChatAnswerCache cache = cache(key);
        cache.setExpiresAt(expiresAt);
        caches.saveAndFlush(cache);
        entityManager.clear();

        ChatAnswerCache reloaded = caches.findByCacheKey(key).orElseThrow();
        assertThat(reloaded.getPublicId()).isNotNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getAnswer()).isEqualTo("cached answer");
        assertThat(reloaded.getPromptVersion()).isEqualTo("v1");
        assertThat(reloaded.getModel()).isEqualTo("gemini-2.5-flash-lite");
        assertThat(reloaded.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(caches.findByCacheKey("b".repeat(64))).isEmpty();
    }

    @Test
    void answer_cache_expires_at_is_optional() {
        String key = "c".repeat(64);
        caches.saveAndFlush(cache(key));
        entityManager.clear();

        assertThat(caches.findByCacheKey(key).orElseThrow().getExpiresAt()).isNull();
    }

    @Test
    void two_caches_with_the_same_key_violate_uniqueness() {
        String key = "d".repeat(64);
        caches.saveAndFlush(cache(key));

        assertThatThrownBy(() -> caches.saveAndFlush(cache(key)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ChatMessage message(UUID sessionId, UUID studentId, UUID tenantId, String status) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setStudentId(studentId);
        message.setTenantId(tenantId);
        message.setQuestion("How do I write an essay?");
        message.setStatus(status);
        return message;
    }

    private static ChatAnswerCache cache(String key) {
        ChatAnswerCache cache = new ChatAnswerCache();
        cache.setCacheKey(key);
        cache.setAnswer("cached answer");
        cache.setPromptVersion("v1");
        cache.setModel("gemini-2.5-flash-lite");
        return cache;
    }
}
