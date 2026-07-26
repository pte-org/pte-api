package com.pte.scoring.vendor;

/** Scores a Write Essay response (grammar/vocabulary/spelling/written discourse). */
public interface EssayScoringClient {

    AiScoreResult score(String essayText, String promptText);
}
