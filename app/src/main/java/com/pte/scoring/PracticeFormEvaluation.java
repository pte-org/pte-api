package com.pte.scoring;

/** Deterministic response-form result used as educational evidence. */
public record PracticeFormEvaluation(int wordCount, String status, String evaluatorVersion,
        String resultHash, String evidenceIdentityHash) {
}
