package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatAiAnswer;
import com.pte.chat.internal.client.ChatTurn;
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
import com.pte.chat.support.GeminiResponses;
import com.pte.chat.support.MutableClock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The chat stack of {@link ChatFlowHarness} (real repositories and SQL, the real {@link GeminiChatClient} over
 * HTTP to a loopback fake provider) with the pieces a load test must look at exposed: the concurrency gate,
 * the meters, and a client wrapper that counts provider calls and the most calls ever in flight at once.
 *
 * <p>The fake provider answers calls in arrival order from a script of {@code scriptLength} responses, each
 * held back for {@code latencyMs}: every tenth call fails with HTTP 500, every fifth (offset 3) is an
 * off-topic verdict, the rest are valid answers.
 */
final class ChatLoadStack implements AutoCloseable {

    static final int FAILING_EVERY = 10;
    static final int OFF_TOPIC_EVERY = 5;
    static final int OFF_TOPIC_OFFSET = 3;

    final FakeGeminiServer server;
    final ChatAiProperties properties = new ChatAiProperties();
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final ChatConcurrencyGate gate;
    final ChatService service;
    final ChatTestData data;
    final AtomicInteger providerCalls = new AtomicInteger();
    final AtomicInteger maxProviderCallsInFlight = new AtomicInteger();

    ChatLoadStack(ChatSessionRepository sessions, ChatMessageRepository messages, ChatAnswerCacheRepository cache,
                  NamedParameterJdbcTemplate jdbc, Consumer<ChatAiProperties> tuning, long latencyMs,
                  int scriptLength) throws IOException {
        server = FakeGeminiServer.start();
        script(latencyMs, scriptLength);
        properties.setEnabled(true);
        properties.setApiKey("AIza-dummy-key_1");
        properties.setModel(ChatFlowHarness.MODEL);
        tuning.accept(properties);
        data = new ChatTestData(jdbc);
        ChatObservability observability = new ChatObservability(meters);
        MutableClock clock = new MutableClock(ChatFlowHarness.START);
        GeminiChatClient client = new CountingClient(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(), server,
                properties, Duration.ofSeconds(10));
        gate = new ChatConcurrencyGate(properties, observability);
        service = new ChatService((caller, organization) -> { }, properties,
                new ChatQuotaService(new ChatQuotaRepository(jdbc), properties, clock, observability),
                new ChatSessionService(sessions, messages, jdbc), gate,
                new ChatAnswerCacheService(cache, jdbc, clock, properties), client, messages, observability, clock,
                studentId -> { });
    }

    private void script(long latencyMs, int length) {
        for (int i = 0; i < length; i++) {
            if (i % FAILING_EVERY == FAILING_EVERY - 1) {
                server.enqueueDelayed(latencyMs, 500, "");
            } else if (i % OFF_TOPIC_EVERY == OFF_TOPIC_OFFSET) {
                server.enqueueDelayed(latencyMs, 200, GeminiResponses.offTopic());
            } else {
                server.enqueueDelayed(latencyMs, 200, GeminiResponses.onTopic("answer " + i));
            }
        }
    }

    /** Sum of every counter with this name, over all tag values. */
    double meter(String name) {
        return meters.getMeters().stream()
                .filter(meter -> meter.getId().getName().equals(name) && meter instanceof Counter)
                .mapToDouble(meter -> ((Counter) meter).count()).sum();
    }

    @Override
    public void close() {
        server.close();
    }

    /** The production client, counting each provider call and the peak number of overlapping calls. */
    private final class CountingClient extends GeminiChatClient {

        private final AtomicInteger inFlight = new AtomicInteger();

        CountingClient(HttpClient http, FakeGeminiServer fake, ChatAiProperties p, Duration timeout) {
            super(http, JsonMapper.builder().build(), fake.baseUri(), p.getApiKey(), p.getModel(), p.getTemperature(),
                    p.getMaxTokens(), timeout);
        }

        @Override
        public ChatAiAnswer generate(String systemInstruction, List<ChatTurn> history, String userMessage) {
            providerCalls.incrementAndGet();
            int now = inFlight.incrementAndGet();
            maxProviderCallsInFlight.accumulateAndGet(now, Math::max);
            try {
                return super.generate(systemInstruction, history, userMessage);
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }
}
