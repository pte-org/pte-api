package com.pte.chat.internal.service;

import com.pte.chat.internal.client.GeminiChatClient;
import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.repository.ChatAnswerCacheRepository;
import com.pte.chat.internal.repository.ChatMessageRepository;
import com.pte.chat.internal.repository.ChatSessionRepository;
import com.pte.chat.internal.service.cache.ChatAnswerCacheService;
import com.pte.chat.internal.service.quota.ChatQuotaRepository;
import com.pte.chat.internal.service.quota.ChatQuotaService;
import com.pte.chat.internal.service.session.ChatConcurrencyGate;
import com.pte.chat.internal.service.session.ChatSessionService;
import com.pte.chat.support.ChatTestData;
import com.pte.chat.support.FakeGeminiServer;
import com.pte.chat.support.MutableClock;
import com.pte.practice.PracticeEntitlementGate;
import com.pte.shared.security.CurrentUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The real chat stack on a real Postgres with only the provider faked: real repositories, real SQL, the real
 * {@link GeminiChatClient} talking to a loopback {@link FakeGeminiServer}. Built by hand (not by Spring) so each
 * test can pick its own limit, slot count, timeout and clock.
 */
final class ChatFlowHarness implements AutoCloseable {

    static final String MODEL = "gemini-2.5-flash-lite";
    /** 2026-10-28 15:00 in Vietnam. */
    static final Instant START = Instant.parse("2026-10-28T08:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 10, 28);

    final FakeGeminiServer server;
    final MutableClock clock = new MutableClock(START);
    final ChatAiProperties properties = new ChatAiProperties();
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final ChatTestData data;
    final ChatAnswerCacheService cache;
    final ChatSessionService sessions;
    final ChatQuotaService quota;
    final ChatService service;

    ChatFlowHarness(ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository,
                    ChatAnswerCacheRepository cacheRepository, NamedParameterJdbcTemplate jdbc,
                    Consumer<ChatAiProperties> tuning, Duration providerTimeout) throws IOException {
        server = FakeGeminiServer.start();
        properties.setEnabled(true);
        properties.setApiKey("AIza-dummy-key_1");
        properties.setModel(MODEL);
        tuning.accept(properties);
        data = new ChatTestData(jdbc);
        ChatObservability observability = new ChatObservability(meters);
        GeminiChatClient client = new GeminiChatClient(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
                JsonMapper.builder().build(), server.baseUri(), properties.getApiKey(), properties.getModel(),
                properties.getTemperature(), properties.getMaxTokens(), providerTimeout);
        quota = new ChatQuotaService(new ChatQuotaRepository(jdbc), properties, clock, observability);
        sessions = new ChatSessionService(sessionRepository, messageRepository, jdbc);
        cache = new ChatAnswerCacheService(cacheRepository, jdbc, clock, properties);
        service = new ChatService((caller, organization) -> { }, properties, quota, sessions,
                new ChatConcurrencyGate(properties, observability), cache, client, messageRepository, observability,
                clock, studentId -> { });
    }

    /** A harness whose entitlement gate lets everyone in. */
    static ChatFlowHarness open(ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository,
                                ChatAnswerCacheRepository cacheRepository, NamedParameterJdbcTemplate jdbc,
                                Consumer<ChatAiProperties> tuning) throws IOException {
        return new ChatFlowHarness(sessionRepository, messageRepository, cacheRepository, jdbc, tuning,
                Duration.ofSeconds(5));
    }

    static CurrentUser student(UUID id) {
        return new CurrentUser(id, UUID.randomUUID(), List.of("STUDENT"));
    }

    ChatReply send(UUID student, UUID session, String message) {
        return service.send(student(student), new ChatSendCommand(session, message, null, "en"));
    }

    ChatReply send(CurrentUser caller, UUID session, String message) {
        return service.send(caller, new ChatSendCommand(session, message, null, "en"));
    }

    /** Waits until the fake provider has received {@code count} requests (the call is then in flight). */
    void awaitProviderRequests(int count) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (server.requestCount() < count) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("provider never received request " + count);
            }
            Thread.sleep(10);
        }
    }

    int used(UUID student) {
        return data.used(student, DAY);
    }

    @Override
    public void close() {
        server.close();
    }
}
