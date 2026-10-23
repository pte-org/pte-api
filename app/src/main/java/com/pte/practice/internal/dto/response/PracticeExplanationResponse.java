package com.pte.practice.internal.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PracticeExplanationResponse(
        UUID publicId,
        UUID itemPublicId,
        String language,
        String status,
        String summary,
        List<String> strengths,
        List<String> improvements,
        List<String> limitations,
        List<RubricFeedback> rubricFeedback,
        String rubricVersion,
        String requestedModel,
        String actualModelVersion,
        String submissionHash,
        Instant generatedAt,
        String failureCode,
        boolean canRetry,
        int attemptCount,
        Instant nextAttemptAt,
        UUID rubricRevisionPublicId,
        String rubricCanonicalHash,
        String rubricProvenanceStatus,
        Integer objectiveScore,
        String objectiveEvaluatorVersion,
        String objectiveResultHash,
        String objectiveEvidenceIdentityHash,
        Integer formWordCount,
        String formStatus,
        String formEvaluatorVersion,
        String formResultHash,
        String formEvidenceIdentityHash) {

    public record RubricFeedback(String criterionId, String level, String evidenceCoverage,
            String feedback, List<String> evidence) {
    }
}
