package com.pte.scoring.internal.vendor.gemini;

import com.pte.itembank.PromptTexts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Sends a scoring call with the system prompt that is in use on the AI prompt pages, so what an examiner or teacher
 * reads there is exactly what the model was given. A call that names a prompt key gets the stored version in use;
 * when nothing is stored yet (before the first seed) the scorer's own catalog-built text is sent. Either way the
 * response says which one went out, with a hash of the exact text, for the score detail.
 */
public class StoredPromptModelClient implements StructuredModelClient {

    private final StructuredModelClient delegate;
    private final PromptTexts prompts;

    public StoredPromptModelClient(StructuredModelClient delegate, PromptTexts prompts) {
        this.delegate = delegate;
        this.prompts = prompts;
    }

    @Override
    public GeminiResponse generate(GeminiRequest request) {
        if (request.promptKey() == null) {
            return delegate.generate(request);
        }
        Optional<PromptTexts.ActivePrompt> stored = prompts.active(PromptTexts.SCORING_CATEGORY, request.promptKey())
                .filter(prompt -> !prompt.text().isBlank());
        GeminiRequest sent = stored.map(prompt -> request.withSystemInstruction(prompt.text())).orElse(request);
        PromptUsed used = stored
                .map(prompt -> new PromptUsed(request.promptKey(), PromptUsed.SOURCE_STORED, prompt.versionNo(),
                        prompt.versionPublicId(), sha256(sent.systemInstruction())))
                .orElseGet(() -> new PromptUsed(request.promptKey(), PromptUsed.SOURCE_CATALOG, null, null,
                        sha256(sent.systemInstruction())));
        return delegate.generate(sent).withPrompt(used);
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
