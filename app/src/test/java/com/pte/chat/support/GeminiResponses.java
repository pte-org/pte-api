package com.pte.chat.support;

import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gemini {@code generateContent} bodies for {@link FakeGeminiServer}, built the way the real API shapes them. */
public final class GeminiResponses {

    public static final String SERVED_MODEL = "gemini-2.5-flash-lite-001";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GeminiResponses() {
    }

    /** A valid on-topic answer: finish STOP, 11 tokens in, 22 out, no thinking. */
    public static String onTopic(String markdown) {
        return modelText(JSON.writeValueAsString(output(true, markdown)), "STOP", 11, 22);
    }

    /** A valid off-topic verdict (empty markdown), still billed 11 in / 5 out. */
    public static String offTopic() {
        return modelText(JSON.writeValueAsString(output(false, "")), "STOP", 11, 5);
    }

    /** Cut off by the token cap: the real client treats it as truncated whatever the text says. */
    public static String maxTokens() {
        return modelText("{\"is_on_topic\":true,\"markdown_content\":\"cut", "MAX_TOKENS", 11, 800);
    }

    /** HTTP 200 and finish STOP, but the model's text is not the JSON object the schema demands. */
    public static String brokenModelText() {
        return modelText("this is {not valid json", "STOP", 11, 22);
    }

    private static Map<String, Object> output(boolean onTopic, String markdown) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("is_on_topic", onTopic);
        output.put("markdown_content", markdown);
        return output;
    }

    private static String modelText(String text, String finishReason, int tokensIn, int tokensOut) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("content", Map.of("role", "model", "parts", List.of(Map.of("text", text))));
        candidate.put("finishReason", finishReason);
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("promptTokenCount", tokensIn);
        usage.put("candidatesTokenCount", tokensOut);
        usage.put("totalTokenCount", tokensIn + tokensOut);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("candidates", List.of(candidate));
        body.put("usageMetadata", usage);
        body.put("modelVersion", SERVED_MODEL);
        return JSON.writeValueAsString(body);
    }
}
