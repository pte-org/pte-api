package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.enums.AiFailureReason;

import java.util.UUID;

/** The two AI-detail facts a list needs, without loading transcripts and feedback for every row. */
public record AiDetailFlags(UUID answerPublicId, boolean fallback, AiFailureReason failureReason) {
}
