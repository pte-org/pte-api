package com.pte.practice.internal.dto.response.mocktest;

/**
 * What pressing "Chấm điểm" on a finished mock test started.
 *
 * @param queuedCount  answers sent to the AI scorer by this request (0 when everything was already requested)
 * @param scoringCount answers of the test still being scored, including the ones just queued
 */
public record PracticeMockTestScoringResponse(int queuedCount, int scoringCount) {
}
