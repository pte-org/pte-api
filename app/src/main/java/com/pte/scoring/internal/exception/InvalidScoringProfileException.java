package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** A pinned template claims a behavior/scorer contract that this release cannot execute. */
public class InvalidScoringProfileException extends DomainException {

    public InvalidScoringProfileException(String taskType, String reason) {
        super(HttpStatus.CONFLICT, ScoringConstants.SCORING_PROFILE_INVALID,
                Map.of("taskType", taskType == null ? "unknown" : taskType),
                ScoringConstants.SCORING_PROFILE_INVALID_MESSAGE, reason);
    }
}
