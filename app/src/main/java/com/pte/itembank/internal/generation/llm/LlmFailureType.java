package com.pte.itembank.internal.generation.llm;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;

/** How a provider call failed, which decides whether to retry and whether the whole job should stop. */
public enum LlmFailureType {
    AUTH(QuestionGenerationConstants.GENERATION_PROVIDER_AUTH),
    UNAVAILABLE(QuestionGenerationConstants.GENERATION_PROVIDER_UNAVAILABLE),
    REJECTED(QuestionGenerationConstants.GENERATION_PROVIDER_REJECTED),
    EMPTY_RESPONSE(QuestionGenerationConstants.GENERATION_EMPTY_RESPONSE);

    private final String code;

    LlmFailureType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
