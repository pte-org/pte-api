package com.pte.itembank.internal.generation.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Reads an OpenAI-compatible streamed chat completion ({@code data: {...}} lines) and hands every piece of text to a
 * {@link TokenSink} as it arrives. A provider that ignores {@code stream=true} and answers with one JSON body is read
 * as well, and its whole answer goes to the sink in one piece. Never logs or quotes the text.
 */
final class LlmStreamReader {

    private static final String DATA_PREFIX = "data:";
    private static final String DONE = "[DONE]";
    private static final String NEWLINE = "\n";

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
            JsonNode delta = chunk.path("choices").path(0).path("delta").path("content");
            if (delta.isTextual() && !delta.asText().isEmpty()) {
                content.append(delta.asText());
                sink.accept(delta.asText());
            }
            if (chunk.path("usage").has("total_tokens")) {
                usage = chunk.path("usage");
            }
        }
        return streamed ? Result.of(content.toString(), usage) : plainAnswer(plainBody.toString(), sink);
    }

    private Result plainAnswer(String body, TokenSink sink) {
        if (body.isBlank()) {
            return Result.of("", jsonMapper.createObjectNode());
        }
        JsonNode root = parse(body);
        JsonNode message = root.path("choices").path(0).path("message").path("content");
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
