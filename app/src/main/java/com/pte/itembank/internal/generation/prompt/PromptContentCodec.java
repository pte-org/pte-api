package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Reads and writes the stored JSON of a prompt, and hashes it so a test can be tied to the exact content it ran on. */
@Component
public class PromptContentCodec {

    private static final String HASH_ALGORITHM = "SHA-256";

    private final JsonMapper jsonMapper;

    public PromptContentCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public PromptContent read(String json) {
        try {
            return jsonMapper.readValue(json, PromptContent.class);
        } catch (JacksonException unreadable) {
            throw new IllegalStateException(QuestionGenerationConstants.PROMPT_CONTENT_UNREADABLE, unreadable);
        }
    }

    public String write(PromptContent content) {
        try {
            return jsonMapper.writeValueAsString(content);
        } catch (JacksonException unwritable) {
            throw new IllegalStateException(QuestionGenerationConstants.PROMPT_CONTENT_UNREADABLE, unwritable);
        }
    }

    /** SHA-256 of the canonical JSON of {@code content}: the same content always gives the same hash. */
    public String hash(PromptContent content) {
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(write(content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
