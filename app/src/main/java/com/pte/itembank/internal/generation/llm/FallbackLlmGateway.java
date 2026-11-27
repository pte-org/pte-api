package com.pte.itembank.internal.generation.llm;

import com.pte.itembank.internal.generation.stop.GenerationStop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Asks the primary provider first and the usual one when it fails: a proxy whose quota ran out, an outage, a refused
 * key. Each provider already retries its own passing failures, so a fallback means the primary gave up. A live view
 * restarts with the second call, because every call begins its text afresh.
 */
public class FallbackLlmGateway implements LlmGateway {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackLlmGateway.class);

    private final LlmGateway primary;
    private final LlmGateway fallback;

    public FallbackLlmGateway(LlmGateway primary, LlmGateway fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public String completeJson(LlmRole role, String systemPrompt, String userPrompt) {
        return withFallback("completeJson", gateway -> gateway.completeJson(role, systemPrompt, userPrompt));
    }

    @Override
    public String completeJsonWithImage(LlmRole role, String systemPrompt, String userPrompt, String mimeType,
                                        byte[] image) {
        return withFallback("completeJsonWithImage",
                gateway -> gateway.completeJsonWithImage(role, systemPrompt, userPrompt, mimeType, image));
    }

    @Override
    public byte[] generateImage(String prompt) {
        return withFallback("generateImage", gateway -> gateway.generateImage(prompt));
    }

    private <T> T withFallback(String call, Function<LlmGateway, T> work) {
        try {
            return work.apply(primary);
        } catch (LlmProviderException failure) {
            GenerationStop.throwIfRequested(); // a stopped job asks nobody else
            LOG.warn("Primary generation provider failed, using the fallback: call={}, code={}", call,
                    failure.getMessage());
            return work.apply(fallback);
        }
    }
}
