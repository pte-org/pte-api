package com.pte.chat.internal.service;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.support.ChatSharedPostgres;
import com.pte.chat.support.ChatTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared setup of the end-to-end chat tests on Postgres. Concrete classes carry
 * {@code @Testcontainers(disabledWithoutDocker = true)}. Every test starts from empty counters and fresh students.
 */
abstract class ChatFlowPostgresBase {

    NamedParameterJdbcTemplate jdbc;
    ChatTestData data;
    UUID alice;
    UUID bob;
    ChatFlowHarness harness;

    @BeforeEach
    void emptyTablesAndFreshIds() {
        jdbc = new NamedParameterJdbcTemplate(ChatSharedPostgres.dataSource());
        data = new ChatTestData(jdbc);
        data.truncateAll();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
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
        harness = ChatFlowHarness.open(jdbc, tuning);
        return harness;
    }

    ChatFlowHarness start(Consumer<ChatAiProperties> tuning, Duration providerTimeout) throws IOException {
        harness = new ChatFlowHarness(jdbc, tuning, providerTimeout);
        return harness;
    }
}
