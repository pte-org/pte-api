package com.pte.chat.internal.service;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.repository.ChatAnswerCacheRepository;
import com.pte.chat.internal.repository.ChatMessageRepository;
import com.pte.chat.internal.repository.ChatSessionRepository;
import com.pte.chat.support.ChatTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared setup of the end-to-end chat tests on Postgres. Concrete classes carry {@code @ChatPostgresJpaTest}.
 * Every test starts from empty chat tables, a fresh student and a fresh session id.
 */
abstract class ChatFlowPostgresBase {

    @Autowired
    ChatSessionRepository sessionRepository;
    @Autowired
    ChatMessageRepository messageRepository;
    @Autowired
    ChatAnswerCacheRepository cacheRepository;
    @Autowired
    NamedParameterJdbcTemplate jdbc;

    ChatTestData data;
    UUID alice;
    UUID bob;
    UUID session;
    ChatFlowHarness harness;

    @BeforeEach
    void emptyTablesAndFreshIds() {
        data = new ChatTestData(jdbc);
        data.truncateAll();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        session = UUID.randomUUID();
    }

    @AfterEach
    void stopProvider() {
        if (harness != null) {
            harness.close();
        }
    }

    ChatFlowHarness start() throws IOException {
        return start(properties -> { });
    }

    ChatFlowHarness start(Consumer<ChatAiProperties> tuning) throws IOException {
        harness = ChatFlowHarness.open(sessionRepository, messageRepository, cacheRepository, jdbc, tuning);
        return harness;
    }

    ChatFlowHarness start(Consumer<ChatAiProperties> tuning, Duration providerTimeout) throws IOException {
        harness = new ChatFlowHarness(sessionRepository, messageRepository, cacheRepository, jdbc, tuning,
                providerTimeout);
        return harness;
    }
}
