package com.pte.scoring.internal.constant;

public final class ScoreReviewConstants {

    public static final String INVALID_SELECTION = "INVALID_SCORE_SOURCE_SELECTION";
    public static final String STALE_REVIEW = "SCORE_REVIEW_VERSION_STALE";
    public static final String SOURCE_UNAVAILABLE = "SCORE_SOURCE_UNAVAILABLE";
    public static final String REQUEST_KEY_REUSED = "SCORE_SOURCE_REQUEST_KEY_REUSED";
    public static final String PUBLISHED_LOCK = "SCORE_REVIEW_LOCKED_AFTER_PUBLICATION";
    public static final String SCOPE_REQUIRED_MESSAGE = "Choose a valid score source and review scope.";
    public static final String STALE_REVIEW_MESSAGE = "Scores changed after preview. Refresh the review and preview again.";
    public static final String SOURCE_UNAVAILABLE_MESSAGE = "The selected source is not available for every matching answer.";
    public static final String REQUEST_KEY_REUSED_MESSAGE = "This request key was already used for a different score selection.";
    public static final String PUBLISHED_LOCK_MESSAGE = "Scores cannot be changed after reports are published.";
    public static final String REASON_SCORING_METHOD_UNAVAILABLE = "SCORING_METHOD_UNAVAILABLE";
    public static final String REASON_NO_SELECTED_SOURCE = "NO_SELECTED_SOURCE";
    public static final String REASON_AI_SCORE_NOT_PUBLISHABLE = "AI_SCORE_NOT_PUBLISHABLE";
    public static final String REASON_EXAMINER_SCORE_NOT_SUBMITTED = "EXAMINER_SCORE_NOT_SUBMITTED";
    public static final String REASON_SELECTED_SCORE_UNAVAILABLE = "SELECTED_SCORE_UNAVAILABLE";

    private ScoreReviewConstants() {
    }
}
