package com.pte.chat.promptsuite;

import com.pte.chat.internal.client.ChatTurn;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Reads the versioned prompt suite from the test classpath. Schema checks live in the data test. */
public final class PromptSuiteLoader {

    public static final String RESOURCE = "/chat/prompt-suite-v1.json";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PromptSuiteLoader() {
    }

    public static JsonNode readTree() {
        try (InputStream in = PromptSuiteLoader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource " + RESOURCE);
            }
            return JSON.readTree(in);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static List<PromptSuiteCase> load() {
        List<PromptSuiteCase> cases = new ArrayList<>();
        for (JsonNode node : readTree().path("cases")) {
            cases.add(toCase(node));
        }
        return List.copyOf(cases);
    }

    static PromptSuiteCase toCase(JsonNode node) {
        List<ChatTurn> history = new ArrayList<>();
        for (JsonNode turn : node.path("history")) {
            history.add(new ChatTurn(turn.path("role").asString(""), turn.path("text").asString("")));
        }
        JsonNode rule = node.path("passRule");
        PromptSuiteCase.PassRule passRule = new PromptSuiteCase.PassRule(
                text(rule, "answerLanguage"),
                strings(rule, "mustNotStartWithPraise"),
                strings(rule, "mustNotContain"),
                rule.hasNonNull("maxWords") ? rule.get("maxWords").asInt() : null,
                strings(rule, "mustContainAny"),
                rule.path("refusalNotEmpty").asBoolean(false),
                text(rule, "reviewNote"));
        return new PromptSuiteCase(node.path("id").asString(""), node.path("group").asString(""),
                node.path("question").asString(""), text(node, "taskType"), node.path("language").asString(""),
                List.copyOf(history), node.path("expectedOnTopic").asBoolean(), passRule);
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asString() : null;
    }

    private static List<String> strings(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : node.path(field)) {
            values.add(item.asString(""));
        }
        return List.copyOf(values);
    }
}
