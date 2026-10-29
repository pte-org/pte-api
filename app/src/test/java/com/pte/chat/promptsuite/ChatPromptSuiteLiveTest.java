package com.pte.chat.promptsuite;

import com.pte.chat.internal.client.ChatAiAnswer;
import com.pte.chat.internal.client.ChatAiException;
import com.pte.chat.internal.client.ChatAiFailureReason;
import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.client.GeminiChatClient;
import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.config.LiveChatClientFactory;
import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.promptsuite.PromptSuiteRun.CaseResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * OPT-IN live prompt suite: calls the REAL Gemini API (costs money, needs a key). It is skipped unless both
 * {@code CHAT_AI_LIVE_API_KEY} and {@code CHAT_AI_LIVE_MODEL} are set, so it never runs in CI or in the default
 * {@code ./mvnw test}. It uses only the fake text of {@code chat/prompt-suite-v1.json}, never student data.
 *
 * <h3>How to run</h3>
 * Set a budget alert first, then from the {@code pte-api} directory (bash):
 * <pre>
 * CHAT_AI_LIVE_API_KEY='&lt;your key&gt;' CHAT_AI_LIVE_MODEL='gemini-2.5-flash-lite' \
 *   ./mvnw -pl app test -Dtest=ChatPromptSuiteLiveTest -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 * PowerShell:
 * <pre>
 * $env:CHAT_AI_LIVE_API_KEY='&lt;your key&gt;'; $env:CHAT_AI_LIVE_MODEL='gemini-2.5-flash-lite'
 * ./mvnw -pl app test '-Dtest=ChatPromptSuiteLiveTest' '-Dsurefire.failIfNoSpecifiedTests=false'
 * </pre>
 * Optional: {@code CHAT_AI_LIVE_SLEEP_MS} (pause before each call, default 4500, keeps a 15 requests/minute
 * tier happy) and {@code CHAT_AI_LIVE_GROUPS} (for example {@code a,b} to run only some groups; the report then
 * covers only those). The model must start with {@code gemini-2.5-flash-lite}, the same guard as production.
 *
 * <p>Every case runs 3 times with {@code ChatPrompts.systemInstruction()} and the production client. A case
 * fails if any run differs from {@code expectedOnTopic}, ends in MAX_TOKENS, errors, or breaks a machine rule;
 * the rules are never relaxed here. There is no retry: a provider error also fails the run. The key is never
 * printed or written. Output: {@code target/chat-prompt-suite-report.md} (open it and review the hard
 * criteria by eye, see its last section).
 */
@EnabledIfEnvironmentVariable(named = ChatPromptSuiteLiveTest.ENV_KEY, matches = ".+")
@EnabledIfEnvironmentVariable(named = ChatPromptSuiteLiveTest.ENV_MODEL, matches = ".+")
class ChatPromptSuiteLiveTest {

    static final String ENV_KEY = "CHAT_AI_LIVE_API_KEY";
    static final String ENV_MODEL = "CHAT_AI_LIVE_MODEL";
    private static final String ENV_SLEEP = "CHAT_AI_LIVE_SLEEP_MS";
    private static final String ENV_GROUPS = "CHAT_AI_LIVE_GROUPS";
    private static final long DEFAULT_SLEEP_MS = 4_500;
    private static final int RUNS_PER_CASE = 3;
    private static final Path REPORT = Path.of("target", "chat-prompt-suite-report.md");

    private static final List<CaseResult> RESULTS = Collections.synchronizedList(new ArrayList<>());
    private static volatile String abortReason;
    private static volatile ChatAiProperties usedProperties;

    private GeminiChatClient client;
    private ChatAiProperties properties;
    private boolean firstCall = true;

    @TestFactory
    Stream<DynamicTest> everyCaseRunsThreeTimesAndEveryRunMustPass() {
        properties = LiveChatClientFactory.properties(System.getenv(ENV_KEY), System.getenv(ENV_MODEL));
        usedProperties = properties;
        client = LiveChatClientFactory.create(properties);
        Set<String> groups = selectedGroups();
        return PromptSuiteLoader.load().stream()
                .filter(testCase -> groups.isEmpty() || groups.contains(testCase.group()))
                .map(testCase -> DynamicTest.dynamicTest(testCase.id() + " [" + testCase.group() + "]",
                        () -> runCase(testCase)));
    }

    private void runCase(PromptSuiteCase testCase) {
        if (abortReason != null) {
            fail("suite aborted earlier: " + abortReason);
        }
        List<PromptSuiteRun> runs = new ArrayList<>();
        for (int run = 1; run <= RUNS_PER_CASE; run++) {
            pause();
            PromptSuiteRun result = callOnce(testCase, run);
            runs.add(result);
        }
        CaseResult caseResult = new CaseResult(testCase, List.copyOf(runs));
        RESULTS.add(caseResult);
        assertThat(caseResult.failures()).as("case %s (expected is_on_topic=%s)", testCase.id(),
                testCase.expectedOnTopic()).isEmpty();
    }

    private PromptSuiteRun callOnce(PromptSuiteCase testCase, int run) {
        long started = System.nanoTime();
        try {
            ChatAiAnswer answer = client.generate(ChatPrompts.systemInstruction(), testCase.history(),
                    testCase.question());
            long ms = (System.nanoTime() - started) / 1_000_000;
            ChatAiUsage usage = answer.usage();
            return new PromptSuiteRun(run, answer.onTopic(), usage.finishReason(), usage.tokensIn(),
                    usage.tokensOut(), usage.tokensThinking(), ms,
                    PromptSuiteRules.evaluate(testCase, answer.onTopic(), answer.markdown()), answer.markdown(), null);
        } catch (ChatAiException ex) {
            long ms = (System.nanoTime() - started) / 1_000_000;
            if (ex.reason() == ChatAiFailureReason.AUTH_FAILED) {
                abortReason = "the provider rejected the key (AUTH_FAILED)";
            }
            ChatAiUsage usage = ex.usage();
            return new PromptSuiteRun(run, null, usage.finishReason(), usage.tokensIn(), usage.tokensOut(),
                    usage.tokensThinking(), ms, List.of(), null, ex.reason() + " - " + ex.getMessage());
        }
    }

    private void pause() {
        if (firstCall) {
            firstCall = false;
            return;
        }
        try {
            Thread.sleep(sleepMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static long sleepMs() {
        String value = System.getenv(ENV_SLEEP);
        return value == null || value.isBlank() ? DEFAULT_SLEEP_MS : Long.parseLong(value.strip());
    }

    private static Set<String> selectedGroups() {
        String value = System.getenv(ENV_GROUPS);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(List.of(value.strip().toLowerCase().split("\\s*,\\s*")));
    }

    @AfterAll
    static void writeReport() throws IOException {
        if (RESULTS.isEmpty() || usedProperties == null) {
            return;
        }
        List<CaseResult> snapshot;
        synchronized (RESULTS) {
            snapshot = List.copyOf(RESULTS);
        }
        String markdown = PromptSuiteReport.render(snapshot, "v1", usedProperties.getModel(),
                usedProperties.getMaxTokens(), RUNS_PER_CASE, usedProperties.getApiKey());
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, markdown, StandardCharsets.UTF_8);
        System.out.println("Chat prompt suite report written to " + REPORT.toAbsolutePath());
    }
}
