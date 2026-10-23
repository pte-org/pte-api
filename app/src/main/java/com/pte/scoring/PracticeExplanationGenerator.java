package com.pte.scoring;

import java.util.UUID;
import java.util.Set;

/** Public scoring boundary used by practice without importing vendor internals. */
public interface PracticeExplanationGenerator {

    GeneratedExplanation generate(ExplanationRequest request);

    record ExplanationRequest(String taskCode, String prompt, String answer, String referenceAnswer,
            String correctAnswer, String optionsJson, String language, String rubricVersion,
            RubricContext rubricContext, ObjectiveResultContext objectiveResult,
            Set<String> availableEvidenceTypes, Set<String> availableEvidencePaths,
            FormResultContext formResult) {

        public ExplanationRequest {
            availableEvidenceTypes = availableEvidenceTypes == null ? Set.of()
                    : Set.copyOf(availableEvidenceTypes);
            availableEvidencePaths = availableEvidencePaths == null ? Set.of()
                    : Set.copyOf(availableEvidencePaths);
        }

        public ExplanationRequest(String taskCode, String prompt, String answer, String referenceAnswer,
                String correctAnswer, String optionsJson, String language, String rubricVersion) {
            this(taskCode, prompt, answer, referenceAnswer, correctAnswer, optionsJson, language, rubricVersion,
                    null, null, Set.of(), Set.of(), null);
        }

        public ExplanationRequest(String taskCode, String prompt, String answer, String referenceAnswer,
                String correctAnswer, String optionsJson, String language, String rubricVersion,
                RubricContext rubricContext) {
            this(taskCode, prompt, answer, referenceAnswer, correctAnswer, optionsJson, language, rubricVersion,
                    rubricContext, null, Set.of(), Set.of(), null);
        }

        public ExplanationRequest(String taskCode, String prompt, String answer, String referenceAnswer,
                String correctAnswer, String optionsJson, String language, String rubricVersion,
                RubricContext rubricContext, ObjectiveResultContext objectiveResult,
                Set<String> availableEvidenceTypes) {
            this(taskCode, prompt, answer, referenceAnswer, correctAnswer, optionsJson, language, rubricVersion,
                    rubricContext, objectiveResult, availableEvidenceTypes, Set.of(), null);
        }
    }

    record RubricContext(UUID publicId, int revision, String canonicalHash, String documentJson,
            String runtimeCompatibilityHash) {
    }

    /** Provenance of the deterministic objective result available to the tutor. */
    record ObjectiveResultContext(Integer rawScore, String evaluatorVersion, String resultHash,
            String evidenceIdentityHash) {
    }

    /** Deterministic form checks used as evidence, never as an AI score. */
    record FormResultContext(Integer wordCount, String status, String evaluatorVersion,
            String resultHash, String evidenceIdentityHash) {
    }

    record GeneratedExplanation(String summary, String strengthsJson, String improvementsJson,
            String rubricFeedbackJson, String limitationsJson, String modelVersion, String providerVersion,
            Integer objectiveScore, String objectiveEvaluatorVersion, String objectiveResultHash,
            String objectiveEvidenceIdentityHash, Integer formWordCount, String formStatus,
            String formEvaluatorVersion, String formResultHash, String formEvidenceIdentityHash) {
    }
}
