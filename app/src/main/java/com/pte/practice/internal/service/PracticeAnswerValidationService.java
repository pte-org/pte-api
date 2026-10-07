package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.exception.PracticeSessionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Shared practice answer boundary for Phase 05/06. Blank payloads represent a
 * skip/empty draft and may omit confidence; answer-bearing payloads may not.
 */
@Service
public class PracticeAnswerValidationService {

    public void requireConfidenceForAnsweredPayload(String payload, ResponseConfidence confidence) {
        if (payload != null && !payload.isBlank() && confidence == null) {
            throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_CONFIDENCE_REQUIRED,
                    PracticeConstants.PRACTICE_CONFIDENCE_REQUIRED_MESSAGE);
        }
    }
}
