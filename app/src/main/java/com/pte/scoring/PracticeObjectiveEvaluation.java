package com.pte.scoring;

/**
 * Deterministic result for an objective Practice answer, expressed on the shared
 * 0-100 scale. The provenance values make the result reproducible without
 * exposing the answer key to the student.
 */
public record PracticeObjectiveEvaluation(int rawScore, String evaluatorVersion,
        String resultHash, String evidenceIdentityHash) {
}
