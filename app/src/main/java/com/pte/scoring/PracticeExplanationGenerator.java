package com.pte.scoring;

/** Public scoring boundary used by practice without importing vendor internals. */
public interface PracticeExplanationGenerator {

    GeneratedExplanation generate(ExplanationRequest request);

    record ExplanationRequest(String taskCode, String prompt, String answer, String referenceAnswer,
            String correctAnswer, String optionsJson, String language, String rubricVersion) {
    }

    record GeneratedExplanation(String summary, String strengthsJson, String improvementsJson,
            String rubricFeedbackJson, String modelVersion, String providerVersion) {
    }
}
