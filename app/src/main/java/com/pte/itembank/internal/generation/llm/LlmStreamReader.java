package com.pte.itembank.internal.generation.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reads an OpenAI-compatible streamed chat completion ({@code data: {...}} lines) and hands every piece of text to a
 * {@link TokenSink} as it arrives. A provider that ignores {@code stream=true} and answers with one JSON body is read
 * as well, and its whole answer goes to the sink in one piece. A reasoning model's thoughts go to the sink apart from
 * the answer and are not part of it. Never logs or quotes the text.
 */
final class LlmStreamReader {

    private static final String DATA_PREFIX = "data:";
    private static final String DONE = "[DONE]";
    private static final String NEWLINE = "\n";
    private static final String CONTENT = "content";
    /** Where providers stream a reasoning model's thoughts: OpenRouter-style first, then DeepSeek-style. */
    private static final List<String> REASONING_FIELDS = List.of("reasoning", "reasoning_content");

    /** The answer and the token counts the provider reported; -1 when it did not report one. */
    record Result(String content, long totalTokens, long promptTokens, long completionTokens) {

        static Result of(String content, JsonNode usage) {
            return new Result(content, usage.path("total_tokens").asLong(-1), usage.path("prompt_tokens").asLong(-1),
                    usage.path("completion_tokens").asLong(-1));
        }
    }

    private final JsonMapper jsonMapper;

    LlmStreamReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    Result read(InputStream body, TokenSink sink) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        StringBuilder content = new StringBuilder();
        StringBuilder plainBody = new StringBuilder();
        boolean streamed = false;
        JsonNode usage = jsonMapper.createObjectNode();
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith(DATA_PREFIX)) {
                // Blank separators and ": comment" keep-alives of a stream; the whole body of a plain answer.
                if (!streamed) {
                    plainBody.append(line).append(NEWLINE);
                }
                continue;
            }
            streamed = true;
            String data = line.substring(DATA_PREFIX.length()).trim();
            if (DONE.equals(data)) {
                break;
            }
            if (data.isEmpty()) {
                continue;
            }
            JsonNode chunk = parse(data);
            if (chunk.has("error")) {
                throw new LlmProviderException(LlmFailureType.UNAVAILABLE);
            }
            JsonNode delta = chunk.path("choices").path(0).path("delta");
            String thought = reasoning(delta);
            if (!thought.isEmpty()) {
                sink.thinking(thought);
            }
            JsonNode text = delta.path(CONTENT);
            if (text.isTextual() && !text.asText().isEmpty()) {
                content.append(text.asText());
                sink.accept(text.asText());
            }
            if (chunk.path("usage").has("total_tokens")) {
                usage = chunk.path("usage");
            }
        }
        return streamed ? Result.of(content.toString(), usage) : plainAnswer(plainBody.toString(), sink);
    }

    private static String reasoning(JsonNode delta) {
        for (String field : REASONING_FIELDS) {
            JsonNode thought = delta.path(field);
            if (thought.isTextual() && !thought.asText().isEmpty()) {
                return thought.asText();
            }
        }
        return "";
    }

    private Result plainAnswer(String body, TokenSink sink) {
        if (body.isBlank()) {
            return Result.of("", jsonMapper.createObjectNode());
        }
        JsonNode root = parse(body);
        JsonNode message = root.path("choices").path(0).path("message").path(CONTENT);
        String text = message.isTextual() ? message.asText() : "";
        if (!text.isEmpty()) {
            sink.accept(text);
        }
        return Result.of(text, root.path("usage"));
    }

    private JsonNode parse(String json) {
        try {
            return jsonMapper.readTree(json);
        } catch (RuntimeException unparsable) {
            throw new LlmProviderException(LlmFailureType.REJECTED);
        }
    }
}
