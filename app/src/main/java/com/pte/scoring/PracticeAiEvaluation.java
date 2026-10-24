package com.pte.scoring;

import java.time.Instant;
import java.util.Map;

/**
 * Score of a practice response. The record is owned by scoring so Practice can
 * consume Azure, Gemini or the objective scorer without importing vendor
 * implementations or creating an exam attempt. Objective results carry no
 * transcript, detail or feedback.
 */
public record PracticeAiEvaluation(int rawScore, Map<String, Integer> subScores,
        String scoreStatus, String provider, String model, String providerVersion, String transcript,
        String detailJson, Instant evaluatedAt, String resultHash, String feedback) {

    public PracticeAiEvaluation {
        if (rawScore < 0 || rawScore > 100) {
            throw new IllegalArgumentException("Practice AI score must be between 0 and 100");
        }
        subScores = subScores == null ? Map.of() : Map.copyOf(subScores);
        feedback = feedback == null ? "" : feedback;
    }

    /** The candidate's transcript, detail and feedback never reach a log line. */
    @Override
    public String toString() {
        return "PracticeAiEvaluation[rawScore=" + rawScore + ", scoreStatus=" + scoreStatus
                + ", provider=" + provider + ", model=" + model + "]";
    }
}
