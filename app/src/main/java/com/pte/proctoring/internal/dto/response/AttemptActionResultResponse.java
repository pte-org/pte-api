package com.pte.proctoring.internal.dto.response;

import com.pte.proctoring.internal.constant.ProctorConstants;

import java.util.UUID;

/** Outcome of a proctor action on one attempt: APPLIED, or NO_OP when the attempt was no longer in progress. */
public record AttemptActionResultResponse(UUID attemptPublicId, String outcome) {

    public static AttemptActionResultResponse of(UUID attemptPublicId, boolean applied) {
        return new AttemptActionResultResponse(attemptPublicId,
                applied ? ProctorConstants.ATTEMPT_ACTION_APPLIED : ProctorConstants.ATTEMPT_ACTION_NO_OP);
    }
}
