package com.pte.assessment.internal.service.timing;

import com.pte.assessment.domain.ExamTimeFrame;
import com.pte.assessment.dto.request.UpdateExamTimeFrameRequest;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;

/** Consistency rules of a time frame that field-level validation cannot express. */
public final class ExamTimeFrameRules {

    private ExamTimeFrameRules() {
    }

    /** Builds the frame to store, or throws when a part's minimum exceeds its maximum or the ceiling is too low. */
    public static ExamTimeFrame fromRequest(UpdateExamTimeFrameRequest request) {
        requireOrdered(ExamDefinitionConstants.TIME_FRAME_PART_SPEAKING_WRITING,
                request.speakingWritingMinSeconds(), request.speakingWritingMaxSeconds());
        requireOrdered(ExamDefinitionConstants.TIME_FRAME_PART_READING,
                request.readingMinSeconds(), request.readingMaxSeconds());
        requireOrdered(ExamDefinitionConstants.TIME_FRAME_PART_LISTENING,
                request.listeningMinSeconds(), request.listeningMaxSeconds());
        long minimumSum = (long) request.speakingWritingMinSeconds() + request.readingMinSeconds()
                + request.listeningMinSeconds();
        if (request.totalMaxSeconds() < minimumSum) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.TIME_FRAME_TOTAL_BELOW_MINIMUM);
        }
        return new ExamTimeFrame(request.speakingWritingMinSeconds(), request.speakingWritingMaxSeconds(),
                request.readingMinSeconds(), request.readingMaxSeconds(), request.listeningMinSeconds(),
                request.listeningMaxSeconds(), request.totalMaxSeconds());
    }

    private static void requireOrdered(String part, int minSeconds, int maxSeconds) {
        if (minSeconds > maxSeconds) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.TIME_FRAME_INVALID_RANGE + part);
        }
    }
}
